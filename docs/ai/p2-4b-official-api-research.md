# P2-4B 공식 API·도구 경계 조사

확인일: 2026-10-10. 대상 작업 트리: `/Users/wooju-kang/.codex/worktrees/p2-4a-generation-contract/planB`.

## 결정에 필요한 사실

- **TourAPI에 영업시간 필드가 있음.** 공식 KorService2 Swagger의 `detailIntro2`는 음식점(39)의 `firstmenu`(대표메뉴), `treatmenu`(취급메뉴), `opentimefood`(영업시간)를 모두 `string`으로 정의. 현재 저장소 `Kor2RestaurantIntroResponse.Item`에는 앞의 두 필드만 있음. P2-4A 결과 문서의 “상세 API에 영업시간이 없음”은 API 계약이 아닌 현재 DTO의 누락으로 정정 필요. [공식 포털의 내장 Swagger](https://www.data.go.kr/data/15101578/openapi.do)
- **Spring AI 2.0.0의 `ChatClient.tools(...)`는 추가 방식.** 공통 `defaultTools(...)`에 전체 도구를 등록한 다음 생성 요청에서 일부 도구를 전달해 제외할 수 없음. 버전 고정 소스의 생성자와 `tools` 구현이 `addAll`/`add` 사용. 현재 `ChatClientConfig`는 `builder.build()`만 호출하고 `OpenAiClient`가 호출마다 `.tools(tools)` 사용. [v2.0.0 DefaultChatClient](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/DefaultChatClient.java)
- **공개 개발계정 신청 가능 트래픽:** TourAPI 1,000, 식약처 식품영양성분DB정보 10,000. 포털은 일일 허용량 초과 코드 22와 초당 초과 코드 23을 안내하나, 이 페이지에 수치 QPS·동시 요청 한도는 없음. 신청 계정의 실제 승인량은 비밀키·계정 화면을 확인하지 않아 미확인. [TourAPI 포털](https://www.data.go.kr/data/15101578/openapi.do), [식약처 포털](https://www.data.go.kr/data/15127578/openapi.do)
- **식약처 공개 현재 명세는 03, 저장소는 02.** 공개 Swagger host는 `apis.data.go.kr/1471000/FoodNtrCpntDbInfo03`, operation은 `getFoodNtrCpntDbInq03`. 저장소 `FoodNtrCpntHandler`는 `getFoodNtrCpntDbInq02` 호출. P2-4B는 02 유지; 공개 03 명세를 02의 응답·제한 보증으로 사용하지 않음. [공식 식약처 Swagger](https://www.data.go.kr/data/15127578/openapi.do)

## 조사 범위와 버전

- 필수 확인: `Kor2RestaurantIntroResponse`, `TourismTool`, `NutritionService`, `docs/ai/p2-4a-generation-contract-result.md`.
- 추가 연결 확인: `PlanTourismTool`, `Kor2ServiceHandler`, `FoodNtrCpntHandler`, `ChatClientConfig`, `McpToolConfig`, `OpenAiClient`, `NutritionEvaluationCollector`.
- `build.gradle`의 Spring AI BOM: **2.0.0**. 현재 온라인 reference는 2.0.1 등으로 이동 가능하므로 **v2.0.0 태그 소스** 우선.
- Reactor: 보존된 externalTest 보고서에 `reactor-core-3.8.2.jar` 표기. **v3.8.2 태그 소스·FAQ** 확인. 새 Gradle 실행 또는 의존성 다운로드 없음.
- TourAPI: 공개 내장 Swagger **2.0 형식 / info.version 1.0.0 / KorService2**. 포털 수정일 2026-02-26.
- 식약처: 공개 내장 Swagger **2.0 형식 / info.version 1.0.0 / FoodNtrCpntDbInfo03**. 포털 수정일 2025-12-05. 02의 폐기 여부는 이번 자료에서 확인 불가.
- 공식 공개 페이지와 소스만 조회. 실제 서비스 API 호출·유료 호출·인증키·환경 설정 비밀값 조회 없음. Swagger JSON은 공식 포털 HTML의 `const swaggerJson`에서 메모리로 파싱; 별도 다운로드 파일 없음.

## KorService2 소개정보 계약

공식 `detailIntro2`의 query 명세:

| 파라미터 | 필수 | 의미·허용값 |
|---|---|---|
| `serviceKey` | 예 | 포털 발급 인증키 |
| `MobileOS` | 예 | IOS / AND / WEB / ETC |
| `MobileApp` | 예 | 서비스명 |
| `contentId` | 예 | 콘텐츠 ID |
| `contentTypeId` | 예 | 12 / 14 / 15 / 25 / 28 / 32 / 38 / **39(음식점)** |
| `_type` | 아니오 | json 지정, 기본 XML |
| `numOfRows` | 아니오 | 한 페이지 결과수 |
| `pageNo` | 아니오 | 페이지번호 |

`firstmenu`, `treatmenu`, `opentimefood`, `restdatefood`(쉬는날)는 음식점 문자열 필드. 반환값 필수 여부, 영업시간 문자열의 표준 문법·타임존·브레이크타임·공휴일 처리·실시간 영업 상태 보증은 이 Swagger에 없음. **문자열 원문 표시 가능과 일정의 영업 가능 판정은 별개 계약**. 이 자료만으로 `10:00~22:00` 같은 정형 포맷이나 매 항목 값의 존재를 가정할 수 없음. [공식 Swagger](https://www.data.go.kr/data/15101578/openapi.do)

저장소 handler의 ETC / PlanB / json / contentTypeId39 / numOfRows1 / pageNo1은 공개 명세의 매개변수와 부합. `candidateId` 접두사 제거 후 TourAPI 콘텐츠 ID 사용이라는 현재 경계 유지 권고. `opentimefood` 추가 시 null·빈 값 허용, 신뢰할 수 없는 시간을 생성하지 않고 원문 보존 권고. 공개 자료가 실시간 문 열림을 보증하지 않으므로 정교한 영업 가능 판정은 별도 범위.

## 식약처 제한과 영양 계약

공식 페이지는 개발 10,000, 운영은 활용사례 등록 후 트래픽 증가 신청 가능으로 표기. 비용 무료, 개발 자동승인·운영 심의승인. 일일 초과(22), 초당 초과(23), timeout(05) 정의는 있지만 **숫자 QPS·허용 동시성·권장 concurrency는 공개하지 않음**. 따라서 동시성 2/4나 특정 대기시간을 공급자 보장으로 표현하면 안 됨. [공식 포털](https://www.data.go.kr/data/15127578/openapi.do)

현재 공개 03에는 `serviceKey` 필수, `pageNo`, `numOfRows`, `type(xml/json)`, `FOOD_NM_KR`, `DB_CLASS_NM` 등 선택 검색값이 존재. 이것은 03 확인이며, 02 유지 결정과 혼동하지 않음. 포털 설명은 기준량·1회섭취 참고량을 데이터 범주로 소개하지만 특정 검색 행에서 유효한 1회분량을 보증하지 않음. 현재 `NutritionService`의 수치 보존 / `NOT_EVALUABLE` / 조회 실패 `UNAVAILABLE` 계약은 그대로 유지 권고. [공식 명세·설명](https://www.data.go.kr/data/15127578/openapi.do)

저장소는 메뉴명 우선 검색, 빈 결과에 다른 `standardFoodName`으로 한 번 재조회. 각 조회 timeout15초, found TTL30일 / empty TTL1일, blocking Redis는 boundedElastic 처리. Java 후처리에 생성 선택의 메뉴명과 `standardFoodName`을 함께 전달하고 질환은 서버의 원본 요청 사용 권고. 같은 메뉴가 여러 번 등장하면 요청 내 중복 조회 억제 가능. 실제 식당 조리법·1회분량의 추정 또는 질환 LOW/CHECK/HIGH 계약 변경은 이번 이동의 근거에 포함하지 않음.

## Spring AI 2.0.0 도구 집합 분리

`ToolCallbacks.from(objects)`는 `MethodToolCallbackProvider`에 위임. provider는 전달한 객체에서 `@Tool` 메서드를 찾아 callback으로 생성하며, tool 이름 중복을 거부. 단순히 메서드를 public으로 남겨도 `@Tool` 노출과는 별개. [v2.0.0 ToolCallbacks](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-model/src/main/java/org/springframework/ai/support/ToolCallbacks.java), [v2.0.0 MethodToolCallbackProvider](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-model/src/main/java/org/springframework/ai/tool/method/MethodToolCallbackProvider.java)

`DefaultChatClientRequestSpec`는 기본 callback을 복사하고 `.tools(...)`에서 더함. `DefaultChatClientBuilder.defaultTools`도 같은 메서드에 위임. 최종 utils는 그 callback 및 provider 결과를 합친 후 model options에 넣음. [v2.0.0 request 구현](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/DefaultChatClient.java), [v2.0.0 builder](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/DefaultChatClientBuilder.java), [v2.0.0 utils](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/DefaultChatClientUtils.java)

현재 [2.0.1 builder Javadoc](https://docs.spring.io/spring-ai/docs/2.0.1/api/org/springframework/ai/chat/client/DefaultChatClientBuilder.html)은 runtime tools가 기본값을 override한다고 설명하여 v2.0.0 구현과 충돌. **프로젝트 고정 버전 소스와 등록 callback 이름 검증을 채택 근거**로 사용.

최소 권고: 생성 전용 객체를 구성해 검색·카페 검증 도구만 `@Tool`로 노출하고, `getRoute` / `getRestaurantDetail` / `evaluateFoodNutrition`은 생성 Tool 목록에서 제외. 공통 `TourismTool`과 편집용 `PlanTourismTool`의 기존 annotation은 보존. 생성·correction 모두 같은 생성 전용 객체/경계 적용. 상속을 통한 annotation 재노출보다 별도 composition adapter가 검토하기 쉬움. callback 이름 집합 테스트로 생성 제외와 편집 유지 증명. 공급자 MCP auto-configuration이나 builder default에 추가 도구가 들어오는 경우도 실제 최종 callback 목록으로 확인.

## Reactor와 동기 MVC 경계

`flatMapSequential(mapper,maxConcurrency,prefetch)`는 inner를 일찍 구독하면서 원본 순서로 emit. 빠른 후속 inner는 앞선 inner가 끝날 때까지 대기; `maxConcurrency`는 in-flight inner 개수, prefetch는 inner별 요소 수. 이것은 요청 단위 제한이며 앱 전체 QPS 제한이 아님. [Reactor v3.8.2 Flux 소스](https://github.com/reactor/reactor-core/blob/v3.8.2/reactor-core/src/main/java/reactor/core/publisher/Flux.java)

동기 blocking 호출을 inner로 써야 한다면 `Mono.fromCallable(...)`와 `subscribeOn(Schedulers.boundedElastic())` 조합. 단순 `flatMapSequential(x -> Mono.just(blockingCall(x)),...)`는 mapper 안에서 바로 blocking해 병렬 호출 의도 달성 불가. 이미 `Mono`를 반환하는 WebClient handler는 직접 조합 가능. [Reactor v3.8.2 FAQ](https://github.com/reactor/reactor-core/blob/v3.8.2/docs/modules/ROOT/pages/faq.adoc)

현재 동기 서비스 계약에서는 여러 요청을 조합하고 끝에서 `collectList().block()` 한 번 사용 권고. Reactor nonblocking thread에서는 block 금지. [Reactor v3.8.2 BlockingSingleSubscriber](https://github.com/reactor/reactor-core/blob/v3.8.2/reactor-core/src/main/java/reactor/core/publisher/BlockingSingleSubscriber.java). Spring 공식 문서도 여러 WebClient 응답을 개별 block하기보다 조합 후 대기하는 패턴을 설명하며 controller에서는 reactive 반환을 권장. 여기서는 전체 MVC 전환 없이 기존 서비스 경계 유지라는 제한된 선택. [Spring Framework synchronous WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-synchronous.html)

## P2-4B 최소 구현 권고

1. 음식점 검색 결과를 모델에 반환하기 전에 Java가 상세 조회해 메뉴 원문을 붙임. 모델이 아직 보지 못한 상세 메뉴를 최종 선택 뒤 조회하는 방식만으로는 현재 메뉴 출처 계약 유지 불가.
2. 생성의 개별 `getRoute` Tool은 제거하고 확정 일정 후 기존 Java 이동시간 resolver 사용. `findPlaceWithRoute`의 현재 카페 확인·경로 계약은 별도 범위 검토 없이는 변경하지 않음.
3. 생성 선택의 메뉴명·표준 품목명을 Java 영양 후처리에 전달. collector의 요청 컨텍스트 및 ThreadLocal 의존성을 비동기 작업에 전달한다고 가정하지 않음; 평가 결과를 값으로 collect 후 원래 호출 흐름에서 반영.
4. 상세 조회의 **요청당 검색 12개 키워드 / 쿼리당 앞 8개 후보 / 총 고유 contentId 48개** 상한, 동일 키워드 응답·contentId 상세의 요청 내 캐시는 검토 가능한 앱 정책 후보. 공급자 명세에 이 수치의 공식 근거 없음. first8 잘라내기로 메뉴 유효 후보가 줄 수 있으므로 기존 품질 gate로 증명 필요.
5. 검색 Tool 안 상세 조회는 우선 순차 방식으로 시작 가능. 상한 때문에 최대 상세 건수가 요청당 48개이며, 캐시 미적중 48개를 실제로 조회하면 TourAPI 개발 공개량1,000 기준 단순 20회 요청 수준에서 상세만으로 960건; 검색·지역코드·기타 호출·실패 재시도는 추가. 실제 승인 quota에 맞춘 총 호출량 관측 필요.
6. 병렬화가 필요하면 낮고 명시적인 concurrency와 원본 순서 보존 사용. 요청 단위 제한만으로 다중 생성 요청의 공급자 전체 제한을 보장하지 않음. 숫자는 성능·오류율 측정으로 결정하며 API 공식 허용량으로 표기하지 않음.

미확인: 계정 실제 승인 quota, 수치 QPS·동시성, 02 폐기 일정, 영업시간 표준 문법과 값 완전성, 위 상한의 품질 영향. 유료·실제 API 결과 없이 문서 조사만으로 성능 개선 채택 판정을 내리지 않음.
