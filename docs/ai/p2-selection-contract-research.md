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

## 별도 조사 — C3r2 장소 유형 선택 실패 진단 (2026-10-11)

조사 대상: `/private/tmp/planb-selection-contract-177`. 아래 내용은 기존 메뉴 선택 연구에 추가한 장소 유형 진단이며, 앞선 역사 기록의 채택 판단을 수정하지 않는다. 사용자 지시에 따라 일정의 총 `travelMinutes`는 현재 필수 채택·수정 조건에서 제외한다. 도보 영향은 미확정 가설이며 이번 장소 유형 실패의 확정 원인으로 사용하지 않는다. 운영 코드·테스트·계획 master 수정, 추가 LLM 또는 공급자 API 호출은 수행하지 않았다.

### 확인한 실패와 계약 경계

기존 측정에는 C3 강릉 2회차의 `PLAN.EXCEPTION.INVALID_AI_PLACE`, 후보 `kakao:8654759`, 원본 유형 `AT4`, 상세 분류 `여행 > 관광,명소 > 호수`, 요청 유형 `PARK_WALK`가 남아 있다. 이 후보·분류는 보존된 측정의 관측값이며 이번 조사에서 최신 Kakao 응답을 다시 조회한 값이 아니다. 근거: [측정 JSON](/private/tmp/planb-selection-contract-177/docs/ai/p2-selection-menu-postfix-actual-quality-20261011.json:2204), [기존 결과 기록](/private/tmp/planb-selection-contract-177/docs/ai/p2-selection-contract-result.md:254).

현재 Java의 `PARK_WALK` 허용 조건은 원본 유형이 Kakao `AT4` 또는 TourAPI `12`이고, `categoryName`이 null이 아니며 `공원` 문자열을 포함하는 경우다. 따라서 위 관측값은 유형 코드를 만족해도 상세 분류 조건에서 거부된다. 같은 Kakao 후보는 유형 조건만 보면 `ATTRACTION`/`MUST_HAVE`에는 허용되지만, 다른 시간·좌표·중복 조건까지 통과한다는 뜻은 아니다. 근거: [allowed](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/domain/travel/helper/PlanPlaceResolver.java:394), [validate 실패 사유](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/domain/travel/helper/PlanPlaceResolver.java:70).

### Kakao 공식 응답 의미와 현재 매핑

- 공식 Local API에서 `category_group_code`는 주요 카테고리를 그룹화한 코드이고 `AT4`의 의미는 관광명소다. `category_name`은 별도의 카테고리 이름이다. 공식 예시는 `>`로 구분한 상세 분류와 빈 그룹 코드를 함께 보여 준다. `AT4`가 공원·호수·산책 가능성 또는 도보 접근성을 보장한다는 계약은 해당 공식 명세에 없다. `PARK_WALK`와 `contains("공원")`은 애플리케이션의 정책이다. [Kakao Local REST API — 키워드로 장소 검색](https://developers.kakao.com/docs/ko/local/dev-guide#search-by-keyword)
- 키워드 검색의 `category_group_code` 요청 인자는 선택적 그룹 필터다. 현재 `searchPlace`는 `query`만 요청하고, `findPlaceWithRoute`는 받은 응답을 Java에서 그룹 코드로 필터링한다. `PARK_WALK` 요청도 `AT4`를 전달하며 상세 분류 필터는 이 검색 단계에 없다. 그러므로 `findPlaceWithRoute(..., PARK_WALK)`의 `found=true`는 최종 `PARK_WALK` 적격 증명이 아니다. 공식 필터 의미: [Kakao Local REST API](https://developers.kakao.com/docs/ko/local/dev-guide#search-by-keyword). 구현: [검색 요청](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/global/client/kakaoMapService/handler/KakaoMapServiceHandler.java:36), [그룹 필터 적용](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/global/client/kakaoMapService/handler/KakaoMapServiceHandler.java:250), [Tool의 유형 매핑](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/ai/mcp/PlanTourismTool.java:207).
- DTO 복원은 `place.category_group_code()`를 `PlaceWithRouteResult.categoryCode`, `place.category_name()`을 `categoryName`에 넣는다. 컨텍스트는 앞의 그룹 코드를 `Candidate.type`에, 상세 분류 이름을 `Candidate.categoryName`에 기록한다. 이 경로에서 상세 분류를 공원으로 바꾸거나 추론하는 단계는 없다. `Candidate.categoryCode`는 이와 달리 TourAPI의 `lclsSystm2`용 필드다. 근거: [toResult](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/global/client/kakaoMapService/helper/KakaoPlaceSearchHelper.java:106), [Kakao 후보 기록](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/ai/context/PlaceCandidateContext.java:81), [Candidate 필드](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/ai/context/PlaceCandidateContext.java:331).

### TourAPI null 분류와 프롬프트·복구 흐름

`PlaceCandidateContext.record(Kor2KeywordSearchResponse.Item)`는 `contenttypeid`를 원본 유형으로, `lclsSystm2`를 `categoryCode`로 저장하지만 `categoryName`에는 항상 null을 넣는다. 검색 DTO에도 `categoryName`은 없고 분류 코드만 있다. 따라서 **현재 이 record 경로로 생성한 모든 `tour:` 후보는 `PARK_WALK` 검증에서 거부**된다. 관광지 유형 `12`도 예외가 아니다. 이는 현재 매핑과 검증의 결정적 결과이며 TourAPI 자체가 모든 공원 정보를 제공하지 않는다는 주장과 구분한다. 근거: [TourAPI 후보 기록](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/ai/context/PlaceCandidateContext.java:55), [검색 Item DTO](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/global/client/kor2Service/dto/response/Kor2KeywordSearchResponse.java:49), [PARK_WALK 조건](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/domain/travel/helper/PlanPlaceResolver.java:404).

프롬프트의 최우선 검증 계약은 `PARK_WALK`를 공원 카테고리가 확인된 **Kakao AT4만** 허용한다. 현재 Java에는 TourAPI `12` 분기도 있지만 null 매핑 때문에 실제로 통과하지 못한다. 이 차이는 잠재 계약 불일치이며 현재 호수 실패의 직접 원인이 TourAPI라는 증거는 아니다. `CourseType`의 사용자 표시명은 `공원·산책`이고 생성 프롬프트의 허용 태그는 `LIGHT_WALK`, `NATURAL_SCENERY`이므로 표시·태그의 넓은 의미와 Java의 좁은 분류 정책도 구분해야 한다. 근거: [VerifiedPlacePrompt](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/ai/prompt/VerifiedPlacePrompt.java:9), [CourseType](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/domain/travel/entity/constant/CourseType.java:23), [생성 태그 지시](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:142).

재선택은 `selectedCandidateId`와 식당 상세만 반환한다. `TravelRecommendHandler.reselectPlace`와 `PlanPlaceResolver.select`는 원본 슬롯의 `courseType`을 유지하며, `PlanService`는 최대 두 번 재선택 후 다시 공통 검증한다. 따라서 원본 슬롯이 `PARK_WALK`이면 후보만 `AT4` 호수로 바꾸거나 모델이 관광지로 생각해도 Java의 요청 유형은 바뀌지 않는다. 관측 기록만으로 최초 선택과 두 복구 시도 각각의 모델 의도·전체 Tool 응답은 확정할 수 없다. 근거: [재선택 DTO](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/ai/dto/response/PlaceReselectResponse.java:5), [재선택 복원](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/ai/handler/TravelRecommendHandler.java:250), [select](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/domain/travel/helper/PlanPlaceResolver.java:243), [제한 복구](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/domain/travel/service/PlanService.java:1484).

### Spring AI 구조화 응답 보장 범위

- Spring AI 공식 converter 문서는 Java 타입에서 JSON Schema를 만들고 모델 출력 텍스트를 Java 객체로 역직렬화하는 역할을 설명하며 converter만 사용하는 경로는 best effort라고 명시한다. native structured output은 공급자 API에 스키마를 보내는 별도 제약이다. [Output Converters](https://docs.spring.io/spring-ai/reference/api/structured-output/converters.html), [Provider-Native Structured Output](https://docs.spring.io/spring-ai/reference/api/structured-output/native.html)
- 이 저장소는 단순 JSON 프롬프트만 쓰는 경로가 아니다. converter 스키마를 정규화해 OpenAI `ResponseFormat.Type.JSON_SCHEMA`로 요청하고 결과 `.content()`를 변환한 뒤 애플리케이션 검증·교정 경로를 사용한다. 따라서 실패를 프레임워크의 JSON 변환 부재로 설명할 근거는 없다. 근거: [OpenAiClient](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/ai/client/OpenAiClient.java:350), [검증 콜백](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/ai/client/OpenAiClient.java:164), [교정 지시](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/ai/client/OpenAiClient.java:402).
- 프로젝트 BOM은 `2.0.0`이다. 해당 버전의 공식 `BeanOutputConverter` 소스에서 `generateSchema()`는 대상 Java 타입으로 스키마를 생성하고 `convert()`는 텍스트 정리 후 `JsonMapper.readValue`를 호출한다. 후보 컨텍스트나 `PlanPlaceResolver.allowed`를 호출하지 않는다. [build.gradle](/private/tmp/planb-selection-contract-177/build.gradle:20), [Spring AI v2.0.0 BeanOutputConverter 원본](https://raw.githubusercontent.com/spring-projects/spring-ai/v2.0.0/spring-ai-model/src/main/java/org/springframework/ai/converter/BeanOutputConverter.java)
- **위 문서·코드에서 도출한 판단:** `courseType`이 Java enum의 `PARK_WALK`이고 `candidateId`가 문자열이라는 구조적 유효성은 그 ID의 공급자 상세 분류가 Java 정책과 일치한다는 의미적 유효성을 포함하지 않는다. 현재 선택 DTO에 공급자 분류 필드가 없고 컨버터 설정은 null 허용과 복약 enum 제외를 보정하므로, 스키마만으로 런타임 후보와 슬롯의 관계를 증명하지 않는다. 생성된 JSON Schema 원문을 이번 조사에서 실행 출력으로 확인한 것은 아니다. 근거: [선택 DTO](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/ai/dto/response/CreatePlanSelection.java:23), [converter 보정](/private/tmp/planb-selection-contract-177/src/main/java/com/planb/global/config/ai/AiOutputConverterConfig.java:40).

조회한 current 공식 문서는 `2.0.1`이다. 새 convenience API의 존재나 자동 재시도 설정을 `2.0.0`의 현재 실행 경로에 소급하지 않는다. 이번 핵심 converter 판단은 `v2.0.0` 공식 소스로 대조했으며 공급자 실제 wire 요청·응답의 strict 설정이나 refusal 동작은 미조회 범위다.

### 호환성을 보존하는 최소 개입 후보 — 정책 미선택

| 후보 | 가능한 개입 범위 | 필요한 판단과 한계 |
| --- | --- | --- |
| 현재 공원 분류 정책 유지 | PARK_WALK 검색 결과·교정 사유에 기존 공원 적격 조건을 일치시켜, 관광명소만 확인된 호수를 최종 선택 전에 구분 | DTO·Tool 이름·원본 유형·시간·복구 상한 보존 가능. Tool 결과 필터링은 현재 반환 의미를 바꾸므로 호출자·테스트 점검 필요. 실제 공원 후보가 없으면 여전히 실패 가능 |
| 공원 외 산책 장소를 명시적으로 허용 | 기존 공통 `allowed` 계층과 검증 프롬프트에 동일한 확정 정책 반영 | 호수라는 분류만으로 안전한 산책·접근성을 추정할 수 없음. 허용 상세 분류와 근거를 결정해야 하며 `AT4` 전체 허용은 별도 정책 확대 |
| 잘못된 선택 유형의 제한 복구 | Java가 후보 근거로 관광지와 산책의 구분을 교정하거나 재선택 계약에 타입 선택을 추가 | 현재 `select`의 원본 유형 유지 계약 변경. tags·필수 지정 장소·저장/편집 흐름 영향 점검 필요. 자동 `PARK_WALK → ATTRACTION` 변환을 현재 계약의 단순 버그 수정으로 단정 불가 |
| TourAPI PARK_WALK 경로 정리 | 기존 Kakao 전용 의도와 Java 분기를 일치시키거나, TourAPI의 명시 분류 근거로 공원 적격성 지원 | 전자는 사실상 도달 불가능한 분기 정리, 후자는 별도 기능·정책 확대. `categoryName=null`을 장소명이나 추정 문자열로 덮어쓰는 방법은 원본 근거 보존에 부적합 |

이번 조사는 어느 정책도 선택·구현하지 않았다. 다음 검증을 한다면 보존된 `AT4/호수` 거부, `AT4/공원` 허용, null 상세 분류 거부, `tour:12/categoryName=null` 거부, 생성과 재선택의 동일 판단을 오프라인 계약 증거로 삼을 수 있다. 성공률 개선·실제 산책 적합성·도보/총 이동시간 영향은 별도 근거가 필요하다.
