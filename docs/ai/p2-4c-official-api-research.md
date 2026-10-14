# P2-4C 생성 후보 사전 조회 공식 API 조사

조사일: 2026-10-10. 기준 소스: **P2-4A 채택 커밋 `2a99375f1316b2652bf6965e61e2f21a4665afde`**. `/Users/wooju-kang/.codex/worktrees/p2-4a-generation-contract/planB`의 HEAD가 이 커밋임을 확인한 뒤 모든 프로젝트 코드를 `git show HEAD:<path>`로 읽었다. 작업 디렉터리의 미커밋 파일은 조사 근거에 포함하지 않았다.

## 결론

**기존 Kor2의 `Mono`를 조합해 관광지와 음식점 조회를 먼저 완료하고, 요청별 후보 원본을 모델 입력과 Java 후보 컨텍스트에 함께 전달하는 방식**을 권고한다. 기존 WebFlux·Reactor 의존성이 있으므로 새 의존성이나 별도 executor가 필요하지 않다. 개별 조회의 `.block()`을 먼저 실행한 뒤 `Mono.zip`으로 묶으면 실제 병렬화가 되지 않는다. Spring Framework 공식 예시도 독립 WebClient 조회를 `Mono.zip`으로 결합한 뒤 한 번 기다리는 방식이다. [기준 build.gradle](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/build.gradle), [공식 WebClient 동기 사용](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-synchronous.html)

이 권고는 구현·실측 결과가 아니다. LLM 왕복 중 검색 단계를 Java로 이동할 때의 최소 설계안이며, 속도·품질은 동일 시나리오의 실제 AI 호출로 별도 입증해야 한다. 로컬 스텁은 Tool 등록·후보 계약·HTTP 경로 회귀 검증에 한정한다.

## 버전과 근거 범위

| 항목 | 채택 코드의 버전/사용 | 조사 근거 |
|---|---|---|
| Java | toolchain 21 | 채택 build.gradle, Oracle Java 21 문서 |
| Spring Boot | 4.0.2 | 채택 build.gradle, Boot `v4.0.2` 소스 및 4.0 문서 |
| Spring AI | BOM 2.0.0 | 채택 build.gradle, Spring AI `v2.0.0` 소스 |
| Reactor | 직접 버전 선언 없음 | Boot 4.0.2가 Reactor BOM 2025.0.2 관리, Reactor Core 3.8.2 API/소스 참고 |

Boot의 관리값은 `v4.0.2` 소스에서 확인했다. **이번 조사에서 Gradle의 실제 resolved dependency graph는 실행하지 않았으므로 Reactor 3.8.2를 실제 해석 버전으로 확정하지 않는다.** 문서의 `current`/`release` URL과 Spring AI `reference/2.0` URL은 현재 문서로 연결되거나 리디렉션될 수 있다. 따라서 프로젝트 버전의 결정적인 동작은 태그 소스로 대조했다. [채택 build.gradle](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/build.gradle), [Boot 4.0.2 의존성 정의](https://raw.githubusercontent.com/spring-projects/spring-boot/v4.0.2/platform/spring-boot-dependencies/build.gradle), [Reactor 3.8.2 API](https://projectreactor.io/docs/core/3.8.2/api/reactor/core/publisher/Flux.html)

## 채택 코드가 보존해야 할 계약

1. `TravelRecommendHandler.createPlanByAi`는 사용자 지정 장소를 먼저 검색하고, `CreatePlanSelection`을 `PlanGenerationSelectionMapper`로 조립한다. 생성 DTO 계약, 후보 ID 해석, 날짜·관광지 개수 validation과 보충 경계를 유지해야 한다. [채택 TravelRecommendHandler](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/src/main/java/com/planb/ai/handler/TravelRecommendHandler.java)
2. 관광지의 지역 범위, 분류·좌표 필터, 무작위 최대 40개 선택은 `TourismTool`의 현재 정책이다. 하위 `Kor2ServiceHandler`를 직접 조합하면서 이 필터를 우회하면 병렬화 외에 후보 품질까지 바뀐다. 기존 동기 Tool은 보존하고 내부 조회용 `Mono` 경로를 추출·재사용하는 편이 좁은 변경이다. [채택 TourismTool](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/src/main/java/com/planb/ai/mcp/TourismTool.java), [채택 Kor2ServiceHandler](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/src/main/java/com/planb/global/client/kor2Service/handler/Kor2ServiceHandler.java)
3. `PlaceCandidateContext.record`는 실제 TourAPI `contentid`에서 `tour:<id>`를 만들고 이름·주소·좌표·이미지·분류를 원본에서 기록한다. 사전 조회도 이 기록 함수를 재사용하고 이름을 후보 ID로 대체하지 않아야 한다. 사용자 지정 장소는 기존 `pin` 의미를 유지한다. [채택 PlaceCandidateContext](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/src/main/java/com/planb/ai/context/PlaceCandidateContext.java)
4. **컨텍스트 초기화가 핵심 경계다.** `OpenAiClient.call(converter, validation, tools)`는 첫 호출 전 `resetCandidates(tools)`를 한 번 실행한다. `PlanTourismTool.resetCandidates`는 후보와 attraction 캐시를 지운다. 따라서 호출 전에 후보 map만 채우면 사전 조회가 사라진다. 요청별 불변 seed를 tool에 보관하고 reset 후 재등록하거나, 생성 전용 호출 경계를 명확히 분리해야 한다. 파싱·교정 재시도에서는 같은 seed와 ID를 유지한다. 현재 수정·재구성용 reset 계약은 함께 확인한다. [채택 OpenAiClient](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/src/main/java/com/planb/ai/client/OpenAiClient.java), [채택 PlanTourismTool](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/src/main/java/com/planb/ai/mcp/PlanTourismTool.java)

## Spring AI 2.0: 명시적 도구 등록과 요청 수명

`v2.0.0`의 `.tools(Object...)`는 넘긴 POJO의 `@Tool` 메서드를 `ToolCallbacks.from(...)`으로 변환한다. `.toolCallbacks(...)`도 지원한다. 따라서 현재처럼 호출마다 `new PlanTourismTool(...)`를 `.tools(...)`에 전달하는 것 자체가 명시적 등록이며, 추가 registry가 필요하지 않다. 같은 소스는 `.prompt()`마다 request spec을 복사하고 `ToolCallingAdvisor`를 자동 등록한다. 생성 경로에서 사용할 검색/enrichment tool을 호출 단위로 제한하고, 공유 `ChatClient` 기본 tool에 요청 후보 상태를 넣지 않는 구성이 적절하다. [Spring AI 2.0 DefaultChatClient](https://raw.githubusercontent.com/spring-projects/spring-ai/v2.0.0/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/DefaultChatClient.java)

`ToolContext` 데이터는 모델에 전송되지 않고 도구 호출 시 전달된다. **모델이 후보를 선택하려면 후보 목록은 prompt 또는 tool 결과에도 있어야 한다.** 후보 상태를 ToolContext에 넣는 것만으로 모델 입력이 되지 않는다. [공식 Tool Context](https://docs.spring.io/spring-ai/reference/api/tools.html#_tool_context)

`ToolContext`의 map은 `v2.0.0`에서 `Collections.unmodifiableMap(context)`로 감싼 view다. 이 구현은 내부 값 객체까지 불변으로 만들거나 복사하지 않는다. 따라서 map에 담은 mutable 후보 객체의 공유 안전성은 애플리케이션 책임이다. 기존 요청별 `PlanTourismTool`/`PlaceCandidateContext` 객체를 유지하면 별도 ToolContext 전환 없이 수명을 명확하게 보존할 수 있다. [Spring AI 2.0 ToolContext](https://raw.githubusercontent.com/spring-projects/spring-ai/v2.0.0/spring-ai-model/src/main/java/org/springframework/ai/chat/model/ToolContext.java)

공식 method-tool 문서는 `Mono`, `Flux`, `Future`, `CompletableFuture` 등을 method tool의 입력·출력으로 지원하지 않는다. 사전 조회의 내부 Java API는 `Mono`여도 모델에 노출하는 `@Tool` 메서드는 기존 동기 DTO/List 결과를 유지한다. [공식 method tool 제한](https://docs.spring.io/spring-ai/reference/api/tools.html#_method_tool_limitations)

## Native structured output

Spring AI 2.0에는 `entity(..., spec -> spec.useProviderStructuredOutput())` 경로가 있다. 그러나 채택 `OpenAiClient`는 이미 `BeanOutputConverter`의 스키마를 nullable/date/time 정책으로 정규화한 뒤 OpenAI `JSON_SCHEMA` 옵션을 사용한다. `v2.0.0` `OpenAiChatModel`은 이 옵션을 SDK의 `ResponseFormatJsonSchema`로 변환하면서 `strict(true)`를 지정한다. **P2-4C는 이 경로와 1회 교정 예산을 유지하는 편이 최소 변경**이다. 새 validation advisor를 추가하면 기존 재시도와 중첩될 수 있으므로 별도 목적 없이 도입하지 않는다. [공식 native output](https://docs.spring.io/spring-ai/reference/api/structured-output/native.html), [Spring AI 2.0 OpenAiChatModel](https://raw.githubusercontent.com/spring-projects/spring-ai/v2.0.0/models/spring-ai-openai/src/main/java/org/springframework/ai/openai/OpenAiChatModel.java), [채택 OpenAiClient](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/src/main/java/com/planb/ai/client/OpenAiClient.java)

스키마가 맞아도 후보 ID의 실제 존재, 선택 유형, 실제 원본 좌표·이미지 보존, 일정 일수와 관광지 수는 별도 Java 도메인 계약이다. 사전 조회는 이 검증과 원본 조립을 대체하지 않는다. [채택 selection mapper](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/src/main/java/com/planb/ai/handler/PlanGenerationSelectionMapper.java), [채택 TravelRecommendHandler](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/src/main/java/com/planb/ai/handler/TravelRecommendHandler.java)

## Reactor: 동시 조회, 순서, 오류

`flatMap(mapper, concurrency)`는 inner publisher를 동시에 구독하지만 완료 순서에 따라 결과가 섞일 수 있다. `flatMapSequential(mapper, maxConcurrency, prefetch)`는 일찍 구독하면서 입력 순서대로 출력하고 늦게 끝나는 앞 조회가 있으면 뒤 결과를 대기시킨다. `maxConcurrency`는 in-flight inner 수, `prefetch`는 각 inner에서 요청할 원소 수다. 한 조회가 `Mono` 한 응답을 반환할 때 `prefetch=1`은 명확한 선택이다. 입력 순서대로 `contentid` 중복을 합칠 때는 `flatMapSequential`이 결과 도착 순서에 따른 우선순위 변화를 막는다. [Reactor 3.8.2 Flux API](https://projectreactor.io/docs/core/3.8.2/api/reactor/core/publisher/Flux.html)

`Mono.zip`은 두 소스가 모두 값을 내야 결합 결과를 만든다. 오류 또는 빈 완료는 다른 소스를 취소하고 각각 오류 또는 빈 완료로 종료한다. 정상 후보 0건은 `List.of()`를 담은 값으로 표현하고, `Mono.empty()`는 예상치 못한 upstream 빈 응답인지 계약상 정상인지 명시한다. `timeout(Duration)`은 제한 안에 값이 없으면 `TimeoutException`을 전달한다. 광범위한 `onErrorResume(... -> emptyList)`는 장애를 정상 0건으로 바꾸므로 필수 사전 조회에는 권고하지 않는다. [Reactor 3.8.2 Mono 소스](https://raw.githubusercontent.com/reactor/reactor-core/v3.8.2/reactor-core/src/main/java/reactor/core/publisher/Mono.java)

취소는 이미 서버가 처리하거나 과금한 요청을 되돌린다는 의미가 아니다. `zip` 실패로 형제 조회가 취소될 수 있다는 보장과 외부 시스템의 실제 중단 여부를 구분해야 한다. 로컬 stub 테스트는 취소 signal과 요청 수를 측정할 수 있지만 공급자 서버의 취소 동작까지 증명하지 않는다. 이는 위 Reactor 취소 계약에서의 설계상 한계다.

**권고 시작값은 독립 관광지 1개 + 음식점 1개 조회의 `zip`, 즉 요청별 최대 2개 조회 branch다.** 여러 음식 키워드가 반드시 필요한 설계라면 요청 키워드를 먼저 중복 제거하고 음식점 쪽 `flatMapSequential(..., 2, 1)`로 시작한다. 이때 관광지 branch 1개와 음식점 inner 2개가 겹치면 최대 3개가 되므로 '전체 동시성 2'라고 보고하면 안 된다. 사용자 지정 장소 조회도 동시에 넣으면 그 수를 추가해야 한다. API별 선후 의존 지역코드 조회는 각 chain 내부의 `flatMap`으로 유지한다. 이 숫자는 공급자 허용량이 아닌 보수적인 실험 설정 제안이다. [Reactor 동시성 계약](https://projectreactor.io/docs/core/3.8.2/api/reactor/core/publisher/Flux.html), [채택 지역코드 chain](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/src/main/java/com/planb/global/client/kor2Service/handler/Kor2ServiceHandler.java)

request-local concurrency는 여러 사용자 요청의 합계를 제한하지 않는다. 동시 생성 요청이 R개이면 branch cap은 최대 R배 증가할 수 있다. 실제 계정 quota와 외부 응답 429·timeout·connection pool 대기를 확인한 뒤 확대해야 한다. **이번 조사에서는 계정별 quota·초당 제한을 확인하지 않았고 새 수치를 가정하지 않는다.** 기준 캐시 코드의 일일 한도 주석은 공급자 계약의 검증 근거로 사용하지 않았다.

## Boot 4 WebClient와 Java 21 선택

Boot 4.0은 사전 설정된 prototype `WebClient.Builder` 주입을 권고한다. builder는 상태를 가지며 변경은 그 builder로 이후 만드는 client에 반영된다. 한 builder로 여러 client를 만드는 경우 `clone()`을 고려한다. 채택 `ApiClient`는 주입 builder로 외부 client를 생성하므로 사전 조회에 새 singleton builder나 HTTP client를 만들 필요가 없다. 기존 timeout·공유 HTTP 자원을 보존한다. [Boot 4.0 REST client 문서](https://docs.spring.io/spring-boot/4.0/reference/io/rest-client.html), [채택 ApiClient](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/src/main/java/com/planb/global/client/ApiClient.java)

Java 21 virtual thread는 주로 blocking I/O의 throughput을 높이며 단일 연산을 더 빠르게 만들지 않는다. non-blocking API를 virtual thread로 감싸도 이점이 작고, virtual thread pool을 동시성 제한 수단으로 사용하지 않아야 한다. Java 21에서는 긴 blocking I/O를 `synchronized` 안에서 실행하면 carrier pinning 위험이 있다. [Oracle Java 21 virtual thread 문서](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html)

따라서 기존 reactive HTTP에는 `parallelStream`, `CompletableFuture.supplyAsync` 또는 virtual-thread executor를 추가하지 않는 방향을 권고한다. 이미 blocking인 Redis는 채택 `Kor2ResponseCache`가 `boundedElastic`로 분리한다. 조회 조합은 reactive 상태로 유지하고 기존 동기 서비스 경계에서 한 번 기다린다. 새 executor와 thread-local 전파 관리가 필요한 blocking wrapper보다 기존 구조의 재사용 범위가 작다. [채택 Kor2ResponseCache](https://github.com/team-planb-dev/BE/blob/2a99375f1316b2652bf6965e61e2f21a4665afde/src/main/java/com/planb/global/client/kor2Service/Kor2ResponseCache.java), [WebClient 결합 대기 예시](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-synchronous.html)

## 구현 검증에 필요한 최소 증거

다음은 공식 동작과 채택 계약에서 도출한 테스트 제안이며 이번 조사에서 실행한 결과가 아니다.

- barrier/delayed stub으로 관광지·음식점이 겹쳐 실행되는지, keyword loop의 실제 최대 in-flight 수가 설정 이하인지 확인
- 결과 완료 순서를 바꿔도 후보 ID·원본 필드와 dedup 우선순위가 같은지 확인
- 성공 0건, 빈 publisher, HTTP 오류, timeout, TourAPI 실패 header를 구분하고 기존 오류 계약과 비교
- 첫 후보 reset 뒤 seed가 남는지, parse/correction 1회 재시도에서 seed ID가 같은지, 서로 다른 생성 요청 사이에 후보가 섞이지 않는지 확인
- 동일 음식 키워드의 중복 조회, 추후 tool 재호출, 후보 부족 보충이 사전 조회 결과를 재사용하는지 확인
- 고정 seed·같은 요청·같은 stub 응답으로 생성 일수, 관광지·식사 수, 지정 장소, 실제 후보 존재, 실패율과 AI/tool 왕복 수를 채택 P2-4A와 비교

유료 API 호출, Git 변경, 소스 수정, Gradle 실행과 실제 운영 latency/quota 검증은 수행하지 않았다. 코드 적용 시 가장 큰 위험은 **초기화로 seed가 지워지는 문제, 실제 원본 필터의 우회, 오류를 0건으로 숨기는 처리, 요청 수에 비례하는 외부 조회 동시성 증가**다.
