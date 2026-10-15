# P2 후속 1–3 공식 근거와 최소 구현 권고

확인일: 2026-10-15. 범위: 최신 요청/취소/확정 상태, SQL rollback 복구, 요청 전체 모델·도구 예산, 영양 기준량. 실제 API·인증키·유료 실행 없음. 공급자 처리량, Kakao 장애, 간헐 테스트, 새 workflow 엔진/DAG는 범위 밖.

## 1. 설치 버전과 근거 우선순위

`build.gradle`: Spring Boot **4.0.2**, Spring AI BOM **2.0.0**, Java **21**. Gradle 캐시에 있는 해당 resolved 계열: Spring Data Redis **4.0.2**, Spring transaction **7.0.3**. 다른 작업으로 7.0.9 소스도 캐시되어 있으므로 캐시 최신 버전을 프로젝트 resolved 버전으로 간주하지 않음.

API 확인은 위 버전의 Maven `*-sources.jar`를 우선 사용. 웹의 `current` 문서는 확인 시 Spring AI 2.0.1/Redis 4.0.7/Framework 7.0.9 등의 후속 버전일 수 있으므로 프로젝트 API 존재 증거와 구분.

설치 공식 소스 위치:

- `/Users/wooju-kang/.gradle/caches/modules-2/files-2.1/org.springframework.ai/spring-ai-client-chat/2.0.0/2a00427c8a4919bf56605401e86132f9d00bf490/spring-ai-client-chat-2.0.0-sources.jar`
- `/Users/wooju-kang/.gradle/caches/modules-2/files-2.1/org.springframework.ai/spring-ai-model/2.0.0/d6757197397a55a119a571bfd086dda5d486460a/spring-ai-model-2.0.0-sources.jar`
- `/Users/wooju-kang/.gradle/caches/modules-2/files-2.1/org.springframework.ai/spring-ai-openai/2.0.0/31d0a9fff102741de81efaf4dfc0ef6f97bcf853/spring-ai-openai-2.0.0-sources.jar`
- `/Users/wooju-kang/.gradle/caches/modules-2/files-2.1/org.springframework.data/spring-data-redis/4.0.2/1e182b977f098bbd674b09339cab76be37a91694/spring-data-redis-4.0.2-sources.jar`
- `/Users/wooju-kang/.gradle/caches/modules-2/files-2.1/org.springframework/spring-tx/7.0.3/d27ac90472f213e9a19c1af9c964f883b63beea7/spring-tx-7.0.3-sources.jar`

## 2. 최신 요청·취소: Redis compare-and-set

Redis Lua는 실행 중 다른 Redis 명령의 끼어듦을 막으므로 token 비교와 preview 저장을 한 script에서 수행 가능. Spring Data Redis `execute`는 공식적으로 check-and-set 용도를 지원. 이는 Redis 내부 원자성이고 SQL 또는 STOMP 원자성이 아님. [Redis scripting](https://redis.io/docs/latest/develop/programmability/eval-intro/), [Spring Data Redis 4.0 scripting](https://docs.spring.io/spring-data/redis/reference/4.0/redis/scripting.html).

최소 권고는 기존 cache repository가 travel별 request token과 상태를 소유하는 것. 임의 숫자 증가보다 UUID를 요청마다 발급하면 만료 후 generation 재사용으로 이전 응답이 통과하는 ABA 문제를 피하기 쉬움. UUID 선택은 제품 구현 판단이며 Redis 지침 자체가 아님.

| 동작 | script가 확인·변경할 계약 |
|---|---|
| begin | 확정 처리 중이 아니면 새 token으로 이전 generation 무효화; 이전 preview·확정 응답이 새 요청 결과처럼 반환되지 않도록 정리 |
| save | 현재 token 일치 + 생성 진행 상태일 때만 본문과 preview 상태 저장; 실패는 정상적인 superseded/cancelled 종료 |
| cancel | 현재 token/state 무효화 + preview 제거를 동일 script에서 수행; 단순 preview 삭제만으로 늦은 write 차단 불가 |
| confirm claim | preview를 삭제하지 않고 token/version + 고유 owner를 원자적으로 확보; 다른 confirm은 busy 또는 해당 generation의 이미 확정된 응답 |
| commit/rollback | 같은 token + 같은 owner에만 후속 상태 전환; 구 요청의 callback이 새 요청을 지우거나 복구하지 못하도록 CAS |

여러 key를 쓰면 `plan-edit:{travelId}:state`, `plan-edit:{travelId}:preview`, `plan-edit:{travelId}:confirmed`처럼 **동일 travel별 hash tag** 필요. `{travelId}`는 실제 ID를 치환한 같은 문자열이며 전체 여행이 한 slot에 모이는 고정 tag는 피함. Lua에 접근할 모든 key를 `KEYS`로 명시. 기존 `plan-edit:ID`와 `plan-edit-confirmed:ID`는 같은 slot 보장이 없으므로 multi-key script에 그대로 사용하면 cluster에서 CROSSSLOT 가능. 단일 hash도 가능한 선택이나 새 상태 저장소 일반화는 불필요. [Redis Cluster hash tags](https://redis.io/docs/latest/operate/oss_and_stack/reference/cluster-spec/), [multi-key operations](https://redis.io/docs/latest/develop/using-commands/multi-key-operations/).

### Lua 직렬화 주의

4.0.2 `DefaultScriptExecutor.java:55–59,103–121`: 기본 `execute(script, keys, args)`는 key serializer로 KEYS, **value serializer로 모든 ARGV와 script 결과** 처리. 현재 `planEditRedisTemplate`의 value serializer는 `JacksonJsonRedisSerializer<EditPlanAiResponse>`이므로 token/TTL을 문자열로 전달하는 기본 overload는 안전하지 않음. JSON 문자열 token에는 따옴표가 포함될 수 있고 TTL도 Lua numeric 문법과 불일치 가능.

최소 안전 선택은 이미 있는 string RedisTemplate/명시적 `StringRedisSerializer` overload로 제어 문자열을 처리하고, preview 본문은 기존 JSON serializer가 만든 UTF-8 JSON 문자열을 그대로 전달·저장하는 방식. 본문 역직렬화는 기존 serializer 유지. 또는 script의 raw byte ARGV를 명시적으로 구성하되 type cast에 기대지 않음. 단일 숫자 script 결과는 `DefaultRedisScript<Long>`의 0/1 같은 상태 코드로 반환하면 payload/result serializer 혼합을 줄일 수 있음. custom serializer overload, script singleton 재사용은 [공식 scripting 문서](https://docs.spring.io/spring-data/redis/reference/4.0/redis/scripting.html)와 설치 4.0.2 소스로 확인.

상태 조회 후 Java에서 save/delete를 호출하는 두 단계 방식은 CAS가 아님. 저장 직전 token 확인만으로도 검사와 저장 사이 경쟁이 남음.

## 3. SQL rollback·동시 confirm: preview 유지 + 소유권

현재 `consumeByTravelId`의 GETDEL 후 SQL write는 SQL rollback에도 Redis 삭제를 되돌리지 않음. 기본 RedisTemplate은 Spring 관리 트랜잭션에 참여하지 않으며, `setEnableTransactionSupport(true)`를 켜도 queued Redis EXEC와 SQL commit을 하나의 분산 commit으로 만드는 보장은 없음. 오히려 script의 즉시 결과가 null이 될 수 있어 claim 판정에 적합하지 않음. [Spring Data Redis transactions](https://docs.spring.io/spring-data/redis/reference/4.0/redis/transactions.html), 4.0.2 `DefaultScriptExecutor.java:69–74`.

권고 순서:

1. 실제 SQL write 트랜잭션 내부에서 preview generation을 claim. payload는 Redis에 유지.
2. claim 직후 synchronization 등록. claim 뒤 SQL 조회/삭제/삽입 어느 지점이 실패해도 callback 범위 포함.
3. SQL 작업 수행. 같은 travel의 confirm/new request/cancel 중 claim 이후 무엇을 허용하는지 명시. 최소 일관성 정책은 확정 시작을 선형화 시점으로 삼아 확정 처리 중 새 요청·취소·다른 확정을 busy로 처리.
4. `afterCommit`: 같은 generation/owner에만 confirmed 응답 기록 + preview 제거 + 소유권 해제. atomic Redis script 한 번 권고.
5. `afterCompletion`: **STATUS_ROLLED_BACK만** 같은 generation/owner의 소유권 해제·preview 재사용 허용. STATUS_COMMITTED를 실패로 복구하지 않음. STATUS_UNKNOWN은 SQL 결과 불확실성이므로 무조건 복구·재확정 허용 금지.

Spring Framework 7.0.3 `TransactionSynchronization`: afterCommit은 SQL commit 이후 호출, RuntimeException은 호출자에게 전파. afterCompletion은 commit/rollback 이후 호출하며 예외는 로그 처리. 해당 callback에서 SQL을 새로 쓰려면 REQUIRES_NEW 필요; 여기서 Redis 조작만 하면 불필요한 새 SQL 트랜잭션은 추가하지 않음. [고정 버전 7.0.3 Javadoc](https://docs.spring.io/spring-framework/docs/7.0.3/javadoc-api/org/springframework/transaction/support/TransactionSynchronization.html).

설치 7.0.3 `AbstractPlatformTransactionManager.java:832–840`는 afterCommit 예외에도 finally에서 STATUS_COMMITTED completion을 발생시킴. 따라서 confirm 호출의 catch에서 Redis 실패를 SQL rollback으로 추정해 preview를 살리면 이미 저장된 변경을 재확정하게 됨. afterCommit 표식 실패의 SQL 성공은 별도 운영 실패/재조회 계약으로 취급.

**보장 한계:** claim TTL이 SQL 진행 중 만료되면 다른 confirm이 동일 preview를 다시 claim할 수 있음. owner UUID 비교는 이전 callback의 다른 owner 삭제는 막지만 이미 진행 중인 SQL write를 중단하지 않음. TX timeout보다 긴 lease만으로 JVM pause/network stall까지 절대 보장한다고 쓰지 않음. SQL row lock/fencing·durable idempotency가 없으면 crash/lease 만료/Redis 장애까지 exactly-once 확정을 주장할 수 없음. TTL, 실제 TX 상한, SQL 경쟁 방지 계약과 이를 시험할 반례를 함께 결정.

Redis token 재확인과 STOMP 발행도 두 시스템의 별도 동작. 재확인 이후 cancel이 먼저 선형화하면 늦은 발행이 여전히 가능. response token을 통한 수신자 stale 거부 또는 해당 여행 발행·취소 직렬화 계약이 없으면 “늦은 메시지 절대 없음”은 미지원 가정. 추가 token 필드나 기존 응답 계약 변경 여부는 구현 시 명시.

## 4. Spring AI 2.0.0 요청 전체 예산

2.0.0 설치 `ToolCallingAdvisor`는 DEFAULT_ORDER = Ordered.HIGHEST_PRECEDENCE + 300. 매 반복에서 `callAdvisorChain.copy(this).nextCall(...)`, 이후 doAfterCall, 그 다음 tool execution 순서. downstream advisor는 **매 모델 왕복에 실행**되며 최종 일반 응답까지 통과. [공식 v2.0.0 소스](https://github.com/spring-projects/spring-ai/blob/v2.0.0/spring-ai-client-chat/src/main/java/org/springframework/ai/chat/client/advisor/ToolCallingAdvisor.java), [advisor 훅 문서](https://docs.spring.io/spring-ai/reference/api/tools/tool-calling-advisor.html).

**버전 차이:** 현재 최신 [tool call limits 문서](https://docs.spring.io/spring-ai/reference/api/tools.html)는 `maxCallsPerTool`, `maxTotalToolCalls`, `ToolCallLimitBehavior`를 설명하지만 설치 **2.0.0** `ToolCallingManager`/`DefaultToolCallingManager`에는 해당 API가 없음. 새 dependency/버전 교체 없이 이를 호출하거나 property만 추가해 제한됐다고 기록할 수 없음.

최소 권고:

- 하나의 요청 예산 객체를 생성·편집의 최상위 orchestration에서 만들고 기존 컨텍스트로 전달. 초기 선택, parse retry, correction, 슬롯 재선택, 식사 보충의 OpenAiClient 호출에 **같은 객체** 공유. `OpenAiClient.call`마다 새 예산을 만들면 전체 요청 상한이 아님.
- 작은 CallAdvisor를 ToolCallingAdvisor보다 뒤(기본 order + 1), ChatModelCallAdvisor보다 앞에 둠. advisor params/context에 같은 mutable budget 객체를 넣고 `chain.nextCall` 직전에 모델 호출 권한 소비. 설치 ChatClientRequest/Response의 context 복사는 shallow map copy이므로 동일 budget 객체 참조 유지 가능. 전역 또는 ThreadLocal 누적 카운터 불필요.
- 반환 ChatResponse에 tool calls가 있으면 tool round 권한을 **도구 실행 이전**에 소비. 초과 시 즉시 비재시도 예외. `hasToolCalls`만으로 1 round 계수하며 한 round 안 tool function 개수는 별도 지표. 함수 수까지 제한할 때는 response의 모든 generations의 toolCalls 합계를 확인.
- budget/cancel 예외를 retryable parse 실패와 구분. 기존 parse/correction 1회와 Java validation은 남기되 남은 요청 예산 안에서만 수행. 제한 초과를 정상 final JSON으로 위조하거나 tool calls를 제거해 성공 처리하지 않음.
- 생성/편집은 현재 blocking 호출 경로. 일반 chat stream까지 제한한다고 보고하려면 별도 StreamAdvisor 계수·종료 경계를 구현·검증해야 함.

설치 `DefaultToolCallingManager.java:174–245`는 한 response의 여러 tool call을 순회. 따라서 **도구 라운드 상한 = 도구 함수 호출 상한**이라는 가정은 틀림. ToolCallingAdvisor 바깥 advisor는 전체 loop 1회를 계수할 뿐 실제 모델 completion을 모두 세지 못함.

### Native SDK 재시도와 물리 요청 수

2.0.0 `OpenAiChatModel.java:217`은 `OpenAIClient.chat().completions().create(request)` 호출. SDK 자체 retry는 이 호출 안에서 발생해 model advisor가 횟수를 보지 못함. `OpenAiSetup.java:171,235`에서 ClientOptions에 maxRetries 전달. 설치 auto-config의 `spring.ai.openai.max-retries`는 해당 native SDK 설정이며 legacy chat options max-retries는 deprecated. [OpenAI chat connection properties](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html).

실제 HTTP completion 요청 상한까지 맞추려면 해당 경로 native SDK max-retries를 0으로 두고 기존 앱 parse/correction 재시도에만 예산을 적용하는 것이 최소안. 재시도를 유지하면 model invocation 상한과 실제 HTTP 시도 상한을 구분하고 SDK 경계 계수가 필요. `spring.ai.retry.max-attempts` 설정만으로 native SDK 반복까지 막혔다고 단정하지 않음. 상한 숫자는 공식 API가 정해 주지 않는 제품 판단; 기존 표본의 최대 요청 길이와 예산 초과 실패 계약을 기준으로 구현자가 명시적으로 결정.

## 5. 영양성분: 기준량과 1회분량

공식 [식약처 공공데이터 페이지](https://www.data.go.kr/data/15127578/openapi.do) HTML에 내장된 Swagger를 인증키 없이 읽음. 2026-10-15 확인 결과 schema info.version **1.0.0**, host **FoodNtrCpntDbInfo03**, operation **getFoodNtrCpntDbInq03**. 현재 프로젝트 handler는 **02** 호출이므로 03 설명을 02 실응답 검증으로 표현하지 않음.

| 공식 03 field | 의미/단위 |
|---|---|
| SERVING_SIZE | 영양성분함량기준량 |
| NUTRI_AMOUNT_SERVING | 1회 섭취참고량 |
| DISH_ONE_SERVING | 1회분량 참고량 |
| Z10500 | 식품중량 |
| AMT_NUM1 / 3 / 4 / 6 / 7 / 8 | 에너지 kcal / 단백질 g / 지방 g / 탄수화물 g / 당류 g / 식이섬유 g |
| AMT_NUM13 / 23 / 24 / 25 | 나트륨 mg / 콜레스테롤 mg / 포화지방산 g / 트랜스지방산 g |

현재 DTO `servingSize`라는 Java 이름이 실제 한 식사 제공량이라는 뜻은 아님. 기준량이 100g이면 영양 숫자는 해당 기준량의 함량이며 restaurant 1인분 또는 실제 섭취량의 증거가 아님. 공식 표준 데이터도 영양성분함량기준량과 식품중량을 다른 칼럼으로 제공하며 100ml 등 부피 기준이 존재. [전국통합식품영양성분정보표준데이터](https://www.data.go.kr/data/15100064/standard.do), [식약처 개별 식품 영양 기준량 표시](https://various.foodsafetykorea.go.kr/nutrient/general/food/detail.do?searchFoodCd=R106-003000001-0000).

최소 권고는 매칭한 DB foodCode·원래 기준량·원래 함량을 함께 보존하고 확정된 동일 단위 분량이 있을 때만 `함량 × 분량 / 기준량` 환산. g↔ml은 밀도 증거 없이 변환 금지, 불명확한 “1인분”·숫자 누락·단위 누락은 판정 불가. 0은 정상 측정값일 수 있어 결측과 구분. DB 대표 음식 매칭은 음식점 실제 재료/조리법/분량 안전성을 입증하지 않음. HIGH/안전 판단의 영양 숫자도 기준량 또는 환산 근거 없이는 성분 확정 판정으로 올리지 않음.

02의 추가 serving fields 존재, 03으로의 API 전환 필요성, 실제 음식점 분량은 이번 공개 문서 연구로 미확정. 자격 없는 실제 API 호출·credential read·새 dependency 없이 확인할 수 있는 범위를 위와 같이 기록.

## 6. 구현 인수 시 최소 반례

- 이전 느린 요청 A, 새 B 완료, A 뒤늦은 save: B 유지. cancel 후 A save: preview 미복구. CAS 후 publish 취소 경쟁의 지원 범위 명시.
- 동시 confirm 2개: SQL write 하나만 허용. claim 직후/SQL flush/commit 실패: rollback은 같은 generation preview 유지. 새 generation 뒤 이전 rollback callback: 새 preview 보존.
- afterCommit Redis 실패: SQL committed, rollback 복구 없음. STATUS_UNKNOWN과 lease expiry/crash는 정상 rollback으로 처리하지 않음.
- 실제 Spring AI chain + 로컬 completion stub: tool loop, initial + parse/correction, 슬롯 재선택의 동일 요청 예산; 마지막 허용 회수 통과·다음 호출 전 실패, 요청 간 카운터 격리. 여러 tools 한 response는 1 round와 N calls 별도.
- JSON serializer를 쓰는 실제 Redis script: token·TTL·payload·0/1 반환. standalone 테스트만으로 cluster 동일 slot 보장 입증했다고 쓰지 않음.
- 영양 100g/100ml/결측/0/불명확 1인분: 기준량 의미 보존, 근거 없는 환산·HIGH 판정 없음.
