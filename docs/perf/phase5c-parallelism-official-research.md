# Phase 5C: Java·외부 OpenAPI 병렬 처리 공식 자료 조사

조사일: 2026-10-06
코드 기준: 활성 Phase 5C 체크아웃 `/Users/wooju-kang/.codex/worktrees/phase5c-stage-metrics/planB`, `6d23bcd`와 조사 시점의 미커밋 변경. 아래 상대 코드 링크는 이 체크아웃의 동일 경로를 기준으로 한다.
범위: AI 모델 호출을 제외한 `WebClient` 기반 조회와 Java 후처리. 이 문서는 구현 결정이나 성능 실측 결과가 아닌, 후보 검토에 필요한 공식 계약의 기록이다.

## 현재 코드에 연결되는 사실

- 이 프로젝트는 Spring Boot 4.0.2, Java 21, Spring MVC와 WebFlux 의존성을 사용한다. 외부 클라이언트 공통 구현은 `WebClient.get().retrieve().bodyToMono(...)`를 반환한다. [빌드 설정](../../build.gradle), [ApiClient](../../src/main/java/com/planb/global/client/ApiClient.java)
- 영양 조회는 메뉴명별 `Mono`를 돌려주지만, 최종 일정의 누락 영양 보강은 현재 메뉴마다 `blockOptional()`을 순차 호출한다. `Kor2ServiceHandler`의 `searchAttractions`, `searchRestaurants`, `searchRestaurantCandidates`는 시·도 `areaCode2`를 먼저 조회한다. 광역 지역은 그 코드로 바로 검색하고, 그 외 지역은 시·군·구 `areaCode2`를 추가 조회한 다음 검색한다. 뒤 단계의 입력이 앞 단계의 결과이므로 한 검색의 지역코드와 본 조회를 그대로 동시에 시작할 수 없다. 별도 `searchKeywordOnly` 경로는 지역코드 조회가 없다. [FoodNtrCpntHandler](../../src/main/java/com/planb/global/client/foodNtrCpnt/handler/FoodNtrCpntHandler.java), [PlanService](../../src/main/java/com/planb/domain/travel/service/PlanService.java), [Kor2ServiceHandler](../../src/main/java/com/planb/global/client/kor2Service/handler/Kor2ServiceHandler.java)
- `KakaoMapServiceHandler.getRoute`는 출발지·도착지 좌표 조회에 이미 `Mono.zip`을 사용하며, 두 좌표를 받은 후 이동수단별 경로를 조회한다. 확정 좌표가 들어오면 해당 좌표 조회는 생략될 수 있다. 같은 경로 내부에 `zip`을 다시 추가하는 것은 새 병렬화 후보가 아니다. [KakaoMapServiceHandler](../../src/main/java/com/planb/global/client/kakaoMapService/handler/KakaoMapServiceHandler.java)
- `NutritionEvaluationCollector`는 요청 중 AI 도구 평가를 `ThreadLocal`에 기록하며 동기 흐름을 전제로 한다. 이 수집기를 거치는 호출을 다른 스레드로 옮기면 명시적 상태 전달이나 다른 저장 방식이 필요하다. 이는 프로젝트 코드에 근거한 위험 평가다. [NutritionEvaluationCollector](../../src/main/java/com/planb/ai/mcp/NutritionEvaluationCollector.java), [Reactor Context propagation](https://projectreactor.io/docs/core/release/reference/advanced-contextPropagation.html)

## 공식 문서가 보장하는 조합 방식

| 경우 | 공식 계약과 적용 판단 |
| --- | --- |
| 독립적인 두 `Mono`의 결과가 모두 필요 | Spring Framework는 각 `WebClient` 호출마다 `block()`하지 않고 `Mono.zip(...).block()`으로 합쳐 한 번 기다리는 동기 사용 예시를 제시한다. `zip`은 모든 소스가 값을 내야 결과를 만들며, 한 소스의 오류 또는 빈 완료는 다른 소스를 취소할 수 있다. 메뉴별 조회에 쓰려면 빈 응답과 실패의 현재 의미를 먼저 보존해야 한다. [Spring WebClient synchronous use](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-synchronous.html), [Reactor Mono.zip](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#zip(java.util.function.Function,reactor.core.publisher.Mono...)) |
| 독립적인 여러 항목을 최대 `n`개씩 조회하고 입력 순서대로 합침 | `Flux.flatMapSequential(mapper, maxConcurrency)`는 내부 Publisher를 일찍 구독하되 원본 순서로 방출하고, `maxConcurrency`는 진행 중인 내부 시퀀스의 최대 수다. 빠르게 끝난 뒤 항목은 앞 항목을 기다리며 버퍼에 놓일 수 있다. 항목당 결과가 `Mono` 하나이면 메뉴나 확정 구간의 순서 복원에 맞는다. [Reactor Flux.flatMapSequential](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#flatMapSequential(java.util.function.Function,int)) |
| 두 번째 조회가 첫 번째 값에 의존 | `Mono.zipWhen`은 첫 결과를 기다려 두 번째 `Mono`를 만든다. `flatMap`도 같은 의존성을 표현한다. 따라서 광역 지역의 시·도 코드 → 검색, 그 외 지역의 시·도 코드 → 시·군·구 코드 → 검색은 각 의존 순서를 유지해야 한다. [Reactor Mono.zipWhen](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#zipWhen(java.util.function.Function)), [Kor2ServiceHandler](../../src/main/java/com/planb/global/client/kor2Service/handler/Kor2ServiceHandler.java) |
| 개별 조회의 시간 제한·실패 | `Mono.timeout(Duration)`은 정해진 시간 안에 값이 없으면 `TimeoutException` 신호를 낸다. `onErrorResume`은 해당 내부 Publisher에서 오류를 처리할 수 있다. Spring `WebClient`의 `retrieve()`는 기본적으로 HTTP 4xx/5xx를 `WebClientResponseException`으로 바꾸며 `onStatus`로 상태별 처리를 지정할 수 있다. 모든 오류를 빈 결과로 바꾸면 영양정보 없음과 외부 장애가 구분되지 않으므로 기존 오류 계약을 먼저 확인해야 한다. [Reactor Mono](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html), [Spring WebClient retrieve](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-retrieve.html) |
| 연결·응답 시간 제한 | Spring은 Reactor Netty `CONNECT_TIMEOUT_MILLIS`, 연결 후 read/write timeout, `responseTimeout`의 전역 또는 요청별 설정 방법을 문서화한다. Reactor `timeout`과 HTTP 클라이언트의 연결·응답 제한은 측정 지점과 실패 형태가 다르므로 제안 단계에서 별도로 정해야 한다. [Spring WebClient configuration](https://docs.spring.io/spring-framework/reference/web/webflux-webclient/client-builder.html#_timeouts) |

`maxConcurrency`는 **한 Flux 구독 내** 동시 진행 수를 제한한다. 여러 사용자 요청이 각각 `flatMapSequential(..., 2)`를 구독하면 외부 공급자에 가는 전체 동시 호출 수는 2를 넘을 수 있다. 이는 연산자의 범위에서 나온 추론이며, 공급자 전체 한도를 지켜야 한다면 별도의 공유 제한 장치가 필요하다. [Reactor Flux.flatMapSequential](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#flatMapSequential(java.util.function.Function,int))

## 호출 수 제거와 병렬화의 구분

한국관광공사는 국문 관광정보 서비스의 지역코드정보, 지역기반관광정보, 키워드검색을 별도 기능으로 제공한다. 현재 프로젝트는 관광지·지역 음식점 후보에 `areaBasedList2`, 음식점 키워드에 `searchKeyword2`를 사용한다. 각 검색은 시·도 `areaCode2`를 선행하며, 광역 지역이 아니면 시·군·구 `areaCode2`도 선행한다. 반환된 `sigunguCode`는 `searchByAreaCode`와 `searchRestaurantByCode` 양쪽의 최종 URI에 실제 전달된다. `null`인 광역 지역은 `DataUriBuilder`가 해당 쿼리 파라미터를 생략한다. 따라서 시·군·구 조회를 무조건 제거하면 현재 검색 범위가 달라질 수 있다. 지역코드를 안전하게 재사용하거나 캐시하는 방안은 **외부 호출 수를 줄이는 최적화**이며, 독립 조회를 동시에 수행하는 병렬화와 구별해야 한다. [공공데이터포털 한국관광공사 서비스](https://www.data.go.kr/data/15101578/openapi.do), [Kor2ServiceHandler](../../src/main/java/com/planb/global/client/kor2Service/handler/Kor2ServiceHandler.java), [DataUriBuilder](../../src/main/java/com/planb/global/client/helper/DataUriBuilder.java)

Reactor `Mono.cache(Duration)`는 첫 구독 결과를 TTL 동안 재사용하고, 같은 캐시 인스턴스에 동시 구독이 들어오면 원본 구독을 한 번만 수행한다. **오류와 빈 완료도 TTL 동안 재생**하므로 지역코드 캐시 적용 시 실패·빈 결과 TTL 또는 별도 무효화 정책을 정해야 한다. `Mono`를 호출마다 새로 만들고 캐시하면 요청 간 재사용은 생기지 않는다. [Reactor Mono.cache](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Mono.html#cache(java.time.Duration))

## 가상 스레드와 상태 전달

Oracle 문서의 가상 스레드 예시는 여러 외부 서비스를 동시에 호출하는 fan-out을 보여준다. 하지만 가상 스레드는 개별 호출을 더 빨리 실행하는 수단이 아니라 주로 높은 동시 요청 처리량을 위한 것이며, 제한된 외부 서비스 접근 수는 스레드 풀 크기가 아닌 `Semaphore` 등으로 제어하라고 명시한다. 이미 `Mono`를 반환하는 현재 경로에서는 Reactor 조합이 먼저 검토할 수 있는 방식이다. 마지막 문장은 코드와 문서에 근거한 적용 판단이다. [Oracle Java 21 Virtual Threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html), [Spring WebClient](https://docs.spring.io/spring-framework/reference/web/webflux-webclient.html)

Reactor는 비동기 시퀀스가 스레드 전환을 할 수 있어 `ThreadLocal`을 자동으로 요청별 상태처럼 취급하면 안 된다고 설명한다. `Context`와 등록된 `ThreadLocalAccessor`로 복원할 수 있지만 설정과 성능 비용이 따른다. Spring AI의 `ToolContext`는 요청별 데이터를 도구 호출에 전달하는 공식 방식이다. 현재 `NutritionEvaluationCollector`를 그대로 둔 채 AI 도구 실행을 병렬화하는 설계는 별도 검증이 필요하다. [Reactor context propagation](https://projectreactor.io/docs/core/release/reference/advanced-contextPropagation.html), [Spring AI Tool Context](https://docs.spring.io/spring-ai/reference/api/tools.html#_tool_context), [NutritionEvaluationCollector](../../src/main/java/com/planb/ai/mcp/NutritionEvaluationCollector.java)

## 공급자 호출량 조건

| 공식 페이지 | 확인된 수치·오류 | 해석 한계 |
| --- | --- | --- |
| [식약처 식품영양성분DB정보](https://www.data.go.kr/data/15127578/openapi.do) | 개발 계정 **신청 가능 트래픽 10,000**, 운영 계정은 활용사례 등록 후 증가 신청 가능. 공공데이터포털 오류 22는 일일 호출 허용량 초과, 23은 초당 호출 허용량 초과. | 10,000을 초당 허용치나 이 프로젝트 키의 실제 승인량으로 읽을 수 없다. |
| [한국관광공사 국문 관광정보 서비스](https://www.data.go.kr/data/15101578/openapi.do) | 개발 계정 **신청 가능 트래픽 1,000**, 운영 계정은 활용사례 등록 후 증가 신청 가능. 오류 22/23의 구분도 같다. | 계정별 실제 승인량과 초당 허용 수치는 프로젝트 설정·포털 승인 내역을 확인해야 한다. |
| [카카오디벨로퍼스 쿼터](https://developers.kakao.com/docs/ko/getting-started/quota) | 문서상 무료 일간 쿼터: REST 키워드 장소 검색 100,000건, 대중교통 경로 조회 1,000건, 카카오내비 REST 자동차 길찾기 10,000건. | 무료 쿼터는 초당 병렬도 한도가 아니다. 현재 앱의 권한·과금·실제 잔량에 따라 적용량이 달라질 수 있다. |

따라서 동시성 수치는 위 표에서 역산하지 않는다. 후보별 외부 호출 수, 전체 요청의 겹침, 실제 키의 승인량, 22/23 오류, 지연 분포를 함께 측정한 뒤 정해야 한다. 특히 지역코드 호출 제거는 지연과 쿼터 사용량을 동시에 줄일 수 있지만, 캐시의 정합성과 장애 처리 계약을 따로 검토해야 한다.
