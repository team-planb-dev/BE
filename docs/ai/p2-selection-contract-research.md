# P2-4A 선택 계약 보완 조사

조회일: 2026-10-10. 조사 대상: `/private/tmp/planb-p24-quality-records`, 읽기 전용.

**결론:** 기존 `candidateId` 선택 DTO와 Tool 등록·실행 설정을 유지하고, 음식점 상세 Tool 결과를 요청 안에 보관하여 `candidateId + menuName` 관계를 Java가 검증한다. 최초 생성과 슬롯 재선택에 같은 규칙을 적용한다. 동적 enum 스키마나 프레임워크 업그레이드는 이 문제 해결에 필요하지 않다는 설계 판단이다.

## 공식 근거

- OpenAI Structured Outputs는 JSON Schema 준수를 지원하지만 값의 오류는 여전히 발생할 수 있다. 따라서 스키마를 통과한 메뉴명도 해당 음식점의 실제 조회 메뉴인지 따로 판단해야 한다. JSON mode는 유효한 JSON을, `json_schema`는 스키마 준수를 목표로 한다. [OpenAI 공식 Structured Outputs 문서](https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=chat)
- Tool 입력 스키마는 모델이 호출할 인자를 정의한다. Tool 실행 결과는 애플리케이션이 반환하며 JSON·오류 코드·텍스트 등 표현을 정할 수 있다. 후보나 메뉴를 응답 데이터로 제공하는 것과 호출 인자의 enum을 만드는 것은 별도 선택이다. [OpenAI 공식 Function Calling 문서](https://developers.openai.com/api/docs/guides/function-calling#formatting-results)
- Spring AI의 `@Tool` 결과는 기본 Jackson 변환기로 직렬화된다. `ToolContext`는 모델에 보내지 않는 내부 데이터를 전달하는 대안이다. 이미 요청별 `PlanTourismTool`이 `PlaceCandidateContext`를 보유하므로 새 `ToolContext` 전달이나 커스텀 결과 변환기가 필수는 아니다. [Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html#_tool_context)
- `BeanOutputConverter`는 Java 타입의 스키마를 만들고 JSON을 Java 객체로 변환한다. 공급자 native schema 설정과 역할이 다르며 함께 사용할 수 있다. 기존 `JSON_SCHEMA` 요청과 converter를 유지하고 의미 검증만 보강하는 방향이 적합하다. [Spring AI Output Converters](https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html), [Spring AI Provider-Native Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output/native.html)
- Spring AI는 Tool 실행 오류를 모델에 전달하거나 호출자에게 던지는 방식을 제공한다. 최종 선택의 도메인 오류는 Tool 실행 실패와 구분해야 한다. 이 조사에서는 기존 `OpenAiClient`의 Java 검증·교정 경로를 재사용하는 것을 권고한다. [Spring AI Tool 예외 처리](https://docs.spring.io/spring-ai/reference/api/tools.html#_exception_handling)

## 현재 코드에서 확인한 공백

| 위치 | 확인한 동작 |
| --- | --- |
| `CreatePlanSelection.java:20`, `PlanGenerationSelectionMapper.java:92` | 최초 생성은 후보 ID와 메뉴 선택을 받으며 장소 정보를 후보에서 복원하지만 `menuName`은 그대로 전달 |
| `TravelRecommendHandler.java:232` | 재선택은 기존 `PlaceReselectResponse`를 사용하며 유효성 콜백 없이 호출; 반환 후보의 존재만 사후 확인 |
| `PlanTourismTool.java:287` | 음식점 상세를 조회·반환하지만 상세 원본을 후보 컨텍스트에 기록하지 않음 |
| `PlaceCandidateContext.java:31`, `:141` | 대표 메뉴 캐시는 있지만 조회 메뉴 집합은 없음; `clear()`는 대표 메뉴 캐시도 비우지 않음 |
| `PlanPlaceResolver.java:99` | 식사 메뉴의 누락·중복을 검사하지만 해당 후보 메뉴인지 검사하지 않음 |
| `OpenAiClient.java:164`, `:391` | `Function<T, List<String>>` 검증 사유와 이전 실패 응답을 교정 요청에 전달하는 제한된 재시도 존재 |
| `MissingSlotCompleter.java:701` | Java 보충 경로도 음식점 상세를 직접 조회하므로 공통 검증을 추가할 때 이 경로의 상세 기록도 필요 |

## 최소 구현 계약 — 공식 문서와 코드에서 도출한 권고

1. 기존 컨텍스트에 후보 ID별 **조회 상세 또는 파싱한 메뉴 집합**을 보관한다. 해당 요청에서 검색한 음식점인지 확인하고, 상세 결과의 `contentid`가 선택 후보의 원본 ID와 같은 항목만 근거로 삼는다. 실패·빈 응답·다른 ID의 상세를 정상 메뉴 근거로 저장하지 않는다. Tool 메서드 이름·인자·반환 DTO와 등록 설정은 유지한다.
2. 메뉴 근거는 `firstmenu`와 `treatmenu`에서 가져온다. 목록 구분자·HTML 표기는 한 곳에서 정규화하고, 선택 메뉴는 개별 메뉴와 일치해야 한다. 단순 부분 문자열·유사도 매칭으로 승인하지 않는다. 원본이 모호하면 검증 실패로 처리한다. `standardFoodName`은 영양 검색용 값이며 실제 음식점 메뉴의 존재 근거가 아니다.
3. 공통 검사 조건은 이번 호출의 후보 존재, 슬롯과 후보 유형 일치, 식사의 비어 있지 않은 메뉴, 동일 후보의 성공한 상세 조회 기록, 메뉴 집합 포함이다. 최초 생성의 selection 검증과 재선택 후 공통 슬롯 검증에서 같은 조건을 호출한다. 재선택의 새 validation 콜백은 기존 두 번의 슬롯 복구와 중첩되므로 추가하지 않는다(설계 리뷰에서 확정). 기존 DTO·편집 흐름·비장소 슬롯 계약과 중복·시간·좌표 검사는 보존한다.
4. 교정 사유에는 슬롯 위치, 후보 ID, 조회 누락/메뉴 불일치와 해당 후보의 허용 메뉴를 짧게 담는다. 조회 누락이면 상세 Tool 호출, 불일치면 조회 메뉴 중 재선택을 요구한다. 정상 후보와 정상 슬롯은 유지하고 기존 재시도 상한을 따른다. 모델 문자열을 Java가 임의 메뉴로 바꿔 성공시키지 않는다.
5. 스냅샷 수명은 한 오케스트레이션 요청과 그 제한된 교정 시도다. 새 요청 시작 시 후보·상세·대표 메뉴 캐시를 함께 초기화하고, 교정에 필요한 성공 조회는 기존 재시도 수명과 맞춘다. Java 보충 경로에서 조회한 상세도 같은 컨텍스트에 기록한다. 전역 캐시·공유 상태·추가 외부 호출 기반 검증은 불필요하다.

## 확인 기준과 버전 제한

오프라인 검증은 정상 메뉴 허용, 다른 음식점 메뉴·부분 문자열·미조회 상세·잘못된 상세 ID 거부, 초기화 후 이전 메뉴 거부, 생성/재선택 규칙 일치, Java 보충 및 기존 편집 흐름 유지에 집중한다. 구현 품질은 별도 `review-loop` 9/10 게이트로 평가해야 하며 이 조사 자체에 점수를 부여하지 않았다.

프로젝트 BOM은 `build.gradle:20`의 **Spring AI 2.0.0**이다. 이번에 조회한 current 공식 문서는 **2.0.1**이고 `/reference/2.0/`도 current로 리다이렉트되었다. 문서의 새 convenience API나 자동 재시도 횟수를 2.0.0에서 지원한다고 단정하지 않는다. 권고안은 저장소에 이미 존재하는 converter·`JSON_SCHEMA`·Tool wrapper·검증 콜백을 재사용한다.

실제 LLM/API 호출, 성능·비용 측정, 코드 수정과 테스트 실행은 수행하지 않았다. TourAPI 메뉴 표기의 실제 변형 범위는 fixture·기존 응답으로 확인해야 하며, 모호한 원본을 거부하면 후보 부족이 늘어날 수 있다.
