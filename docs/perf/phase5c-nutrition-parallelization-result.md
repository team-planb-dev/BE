# Phase 5C 누락 메뉴 영양 조회 병렬화 결과

작성일: 2026-10-06
작업 트리: `/Users/wooju-kang/.codex/worktrees/phase5c-stage-metrics/planB` (`codex/phase5c-stage-metrics`, HEAD `6d23bcd`)
상태: 작성 당시 구현과 테스트 완료. 이후 #137로 dev, #138로 main 병합. 성능 전후 판정은 후속 Grafana 측정에 기록.

## 1. 결론

- AI 응답 이후 기존 평가가 없는 메뉴의 영양 조회를 **요청당 최대 2건 동시 조회 + 원래 메뉴 순서 병합**으로 바꿈
- 메뉴 중복 제거, 호출 수, 조회별 15초 timeout, 조회 불가(`UNAVAILABLE`) 처리, 빈 응답 처리, 예외 전파, `nutrition_lookup` Timer 의미를 보존함
- 구현과 테스트는 독립 검토 2회(7/10 → 9/10)를 거쳐 지적 사항을 반영함
- 전체 테스트 804건 통과, build 성공. 기존 smoke 통합 테스트의 OpenAI·Kor2·Kakao 호출 단언은 바꾸지 않음
- 로컬 smoke 테스트 출력에서 영양 보강 시간이 조회 합계의 약 절반으로 줄었음(§5). 이 값은 Grafana 부하 측정이 아니며 운영 효과 판정이 아님
- **운영 적용은 결정하지 않음.** 요청당 상한 2는 공급자 전체 상한이 아니고, 공급자 계정의 초당·동시 허용량은 확인되지 않았음(§6)

## 2. 변경 범위

| 파일 | 변경 |
|---|---|
| `src/main/java/com/planb/domain/travel/service/PlanService.java` | `enrichMissingNutritionEvaluations`: 누락 메뉴를 먼저 `List`로 확정한 뒤 `Flux.flatMapSequential(..., NUTRITION_LOOKUP_CONCURRENCY = 2)`로 조회하고 `collectList().block()`으로 병합. 메뉴 1건 조회와 Timer를 `lookupNutrition`으로 분리. 기존 `recordStage`와 Timer 생성 코드를 `stageTimer`로 공유 |
| `src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java` | 계약 테스트 8건 추가(§4). PlanServiceTest 전체 37건 |
| `src/test/java/com/planb/unit/domain/travel/service/NutritionServiceTest.java` | 응답 없는 조회가 15초 뒤 `UNAVAILABLE`로 끝나는 가상 시간 테스트 추가 |
| `src/test/java/com/planb/integration/domain/travel/TravelLoadTestSmokeIntegrationTest.java` | 순차 실행 전제 단언 2개를 병렬 계약에 맞게 수정(§4.3) |

이번 변경만 분리한 patch: `phase5c-nutrition-parallel-only.patch` (4개 파일, +455 / -26). 위치는 §8.1에 적음.
작업 트리의 다른 미커밋 변경(단계 계측, `TravelService`, loadtest SLO, 기타 테스트)은 Codex 작업이며 수정하지 않음.

변경하지 않은 것: AI 호출, Tool 실행, `NutritionEvaluationCollector`(ThreadLocal), `NutritionService`·`FoodNtrCpntHandler`, DB 트랜잭션 경계, Kor2 지역코드 재사용, 경로 조회.

## 3. 설계 결정 (grilling 권장안 채택)

| 질문 | 채택 | 근거 |
|---|---|---|
| 병합 방식 | `Flux.flatMapSequential(mapper, 2)` | 내부 Publisher를 동시에 구독하면서 원래 순서로 방출함([Reactor](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#flatMapSequential(java.util.function.Function,int))). 각 결과가 자기 `menuName`을 들고 있어 인덱스로 짝을 맞추지 않음 |
| 순서 계약의 범위 | 기존 순차 결과와 같은 순서 유지. 단, 외부에서 관찰되지 않음 | 소비 측 `nutritionEvaluationsByFoodName`이 메뉴명으로 묶고 보강 대상은 중복이 없음. 테스트는 "역순 완료에도 메뉴별 값이 뒤바뀌지 않음"으로 고정하고, 순서 유지는 코드 주석으로 남김 |
| 동시성 상한 | 요청당 상수 2 | 설정값으로 만들 근거가 아직 없음. 여러 요청을 합친 공급자 전체 상한이 아님을 상수 주석에 명시 |
| 중복 제거 시점 | fan-out 전에 순차 stream으로 `List` 확정 | 병렬 콜백에서 공유 `Set`·`List`를 바꾸지 않음. `parallelStream` 미사용 |
| 오류 | 기존 계약 유지 | HTTP 오류·timeout·미매칭은 `NutritionService`가 이미 `UNAVAILABLE`로 바꿈. 그 밖의 예외는 기존처럼 일정 생성 실패로 전파함. 함께 진행 중이던 다른 조회는 취소됨(기존에는 시작 전이었음) |
| timeout | `NutritionService`의 조회별 15초 그대로 | 바깥 timeout을 추가하지 않음. 이 경로는 표준명이 `null`이라 재조회가 없음 |
| 빈 응답 | 기존처럼 평가를 추가하지 않음 | `Mono.empty()`는 결과 목록에서 빠지고 다른 메뉴는 반영됨 |
| `block()` 위치 | 기존 `blockOptional()`과 같은 호출 위치 | 호출자는 MVC(`TravelFacade`)와 STOMP inbound executor(`ChatMessageFacade`)로 모두 blocking 스레드임. Reactor event loop에서 호출되지 않음 |
| `nutrition_lookup` Timer | 구독 시작부터 해당 메뉴 조회 종료까지 | `Mono.defer` 안에서 시작해 `doFinally`에서 기록. outcome은 하류로 전달하기 전에 `doOnSuccess`/`doOnError`로 확정함 |
| 취소된 조회의 outcome | 새 값 `cancelled` | 다른 메뉴 예외로 취소된 조회를 `failure`로 세면 실패 수가 부풀려짐. 태그 카디널리티는 3값으로 유지됨 |

구현 중 발견한 Reactor 동작: `doFinally`만으로 outcome을 정하면, 오류를 낸 조회 자신도 `CANCEL`로 기록됨. `doFinally`는 오류 신호를 하류에 먼저 넘기는데, 하류의 `flatMapSequential`이 그 순간 모든 내부 구독을 취소하기 때문임. 그래서 outcome을 신호 전달 전에 확정하도록 바꿨고, 테스트로 고정함.

## 4. 테스트

### 4.1 단위 테스트 (`PlanServiceTest`, `makePlanByAi` seam)

| 테스트 | 검증 내용 | 성격 |
|---|---|---|
| 누락 메뉴 영양 조회 최대 2건 동시 진행 | 메뉴 3개에서 동시 진행 최대 2, 호출 3회 | RED 확인 후 GREEN(순차 구현에서 최대 1) |
| 늦게 끝난 앞 메뉴도 자기 영양값 유지 | 앞 메뉴 150ms, 뒤 메뉴 10ms. 메뉴별 탄수화물 값이 뒤바뀌지 않음 | 계약 고정 |
| 조회 불가 메뉴가 다른 메뉴의 영양값을 가리지 않음 | 한 메뉴 `UNAVAILABLE`(지연), 다른 메뉴 값 반영 | 계약 고정 |
| 빈 조회 결과 메뉴는 평가 없이 다른 메뉴만 반영 | `Mono.empty()` 메뉴는 값 없음, 다른 메뉴 반영, `nutrition_lookup` success 2건 | 계약 고정 |
| 조회 예외는 기존처럼 일정 생성 실패로 전파 | 예외 전파, `failure` 1건, 진행 중이던 다른 조회 `cancelled` 1건 | RED 확인 후 GREEN |
| 조회 호출 자체의 예외도 영양 조회 실패로 기록 | `evaluateFoodNutrition`이 동기적으로 예외를 던져도 `failure` 1건 | RED 확인 후 GREEN |
| 영양 조회 단계 시간은 구독부터 메뉴별 완료까지 | 120ms 지연 조회의 Timer 합계 100ms 이상 | 계약 고정 |
| 동시에 처리한 두 일정 생성 요청의 영양값 혼합 없음 | 두 스레드가 동시에 서로 다른 메뉴로 생성, 각 결과가 자기 값만 가짐 | 계약 고정 |

기존 테스트도 그대로 통과함: 보강 대상 0건(`nutrition_lookup` Timer 없음), 중복 메뉴 1회 조회, 서로 다른 메뉴 2회 조회, 기존 평가가 있는 메뉴 제외, 영양 태그.

동시성 테스트는 진행 수 감소를 `doOnTerminate`(완료 신호 전달 전)에서 함. 처음에는 `doFinally`에서 감소시켰는데, `doFinally`는 다음 구독 뒤에 실행될 수 있어 간헐적으로 3이 측정됐음. 수정 후 `--rerun-tasks` 3회 연속 통과함.

### 4.2 `NutritionServiceTest`

- 응답 없는 조회(`Mono.never()`)는 14.999초까지 신호가 없고, 15초 뒤 `UNAVAILABLE`로 완료됨(가상 시간)

### 4.3 smoke 통합 테스트 수정

| 기존 단언 | 수정 | 이유 |
|---|---|---|
| 영양 HTTP 요청이 메뉴 순서대로 도착 | 순서 무관 일치(`containsExactlyInAnyOrderElementsOf`) | 동시 조회에서는 도착 순서가 계약이 아님. 응답의 메뉴 순서(`비빔밥`, `불고기`) 단언은 유지 |
| `enrichment ≥ lookup 합계` | `enrichment ≥ 조회 1건 지연`, 그리고 지연 100ms 이상일 때 `enrichment < lookup 합계` | 순차 실행 전제였음. 새 단언이 두 조회의 겹침을 검증함 |

OpenAI 2회, Kor2 경로, Kakao Mobility, 영양 요청 2건, `nutrition_lookup` 2건 단언은 바꾸지 않음.

### 4.4 전체

- `./gradlew clean test`: 804건, 실패 0, skip 0
- `./gradlew build`: 성공
- 실제 외부 API 테스트(`externalTest`, `FoodNutritionLatencyExternalTest`)는 실행하지 않음

## 5. 로컬 smoke 테스트 출력 (새 관측값)

`TravelLoadTestSmokeIntegrationTest.measuresDelayedNutritionLookupsInTravelCreation`의 `PHASE5C_NUTRITION` 출력임. 같은 JVM 3회 중 첫 회는 초기화 영향이 커서 제외하고 2·3회차를 적음.
- MockMvc 단일 요청이며 부하 측정이 아님
- 비교 기준은 `phase5c-stage-analysis.md`에 기록된 변경 전 측정값(Codex, 2026-10-04~05)으로, 실행 시점이 다름
- 따라서 효과의 방향 확인용이며 전후 판정에 쓰지 않음

| 조건 | 변경 전 기록 (중앙값) | 변경 후 2·3회차 |
|---|---|---|
| OpenAI 0ms, 영양 250ms | 요청 632.05ms, 보강 531.8ms | 요청 363.4 / 355.8ms, 보강 258.3 / 254.7ms, 조회 합계 515.1 / 508.3ms |
| OpenAI 3,000ms, 영양 4,000ms | 요청 14,190.5ms, 보강 8,024.75ms | 요청 10,189.4 / 10,171.2ms, 보강 4,022.7 / 4,008.3ms, 조회 합계 8,039.3 / 8,014.4ms |

두 조건 모두 영양 HTTP 요청은 2건, `nutrition_lookup`은 2건이었음. 보강 시간이 조회 1건 지연 수준으로 줄어 두 대기가 겹친 것을 확인함.

### 4.5 메트릭 의미 변경

- `planb.travel.plan.stage{stage="nutrition_lookup"}`의 outcome에 `cancelled`가 추가됨. 다른 메뉴 조회의 예외로 취소된 조회에만 쓰임. 다른 stage는 기존처럼 `success`/`failure`임
- 메뉴별 조회가 요청당 최대 2건 겹치므로 `nutrition_lookup` 시간 합계는 `nutrition_enrichment`(wall-clock)보다 클 수 있음
- 3번째 이후 메뉴의 `nutrition_lookup` 시간은 앞 조회를 기다린 대기를 포함하지 않음. 구독 시작부터 측정하기 때문임
- 기존 소비 코드(테스트, loadtest SLO 설정, Grafana 대시보드)는 `outcome=success` 필터나 meter 이름만 사용해 영향이 없음을 확인함
- `phase5c-stage-analysis.md`의 "outcome(`success`/`failure`)뿐"과 `nutrition_lookup` 설명은 Codex 문서라 수정하지 않았음. Codex가 위 세 가지를 반영해 갱신해야 함

## 6. 남은 위험

- **공급자 호출 밀도 증가**: 호출 수는 같지만 한 요청 안에서 동시에 최대 2건이 나감. 여러 요청이 겹치면 공급자로 가는 동시 호출은 2를 넘음. 공급자 계정의 초당·동시 허용량은 공개 자료에 없고 확인되지 않음. 제한 초과 오류 코드 23은 현재 `FoodNtrCpntHelper`가 `header.resultCode`를 검사하지 않아 `UNAVAILABLE`과 섞일 수 있음. 운영 적용 전에 애플리케이션 전체 상한과 23·HTTP 오류·timeout·미매칭 구분 계측이 필요함
- **예외 시 취소**: 예외가 나면 진행 중이던 다른 조회가 취소됨. 결과는 기존과 같은 요청 실패이고, 실제로는 `NutritionService`가 오류를 `UNAVAILABLE`로 바꾸므로 드문 경로임
- **편집 경로**: `flow=edit`에서도 같은 메서드를 쓰지만 병렬 동작을 편집 경로 단위 테스트로 따로 검증하지 않음
- **실제 API 효과**: 실제 API로 병렬 경로를 실행해 보지 않음. 한도 탐색 부하는 보내지 않음
- **품질 문제와 별개**: `불고기` 미매칭(`UNAVAILABLE`)은 병렬화로 해결되지 않음
- **통합 단언의 여유**: 250ms 지연에서 `enrichment < lookup 합계` 단언은 250ms 넘는 GC·CI 정지가 겹치면 실패할 수 있음. 로컬 3회·전체 실행 2회에서는 통과함

## 7. 롤백

- `git apply -R <patch>`로 이번 변경만 되돌릴 수 있음. 작업 트리의 다른 미커밋 변경은 영향받지 않음
- 운영 동작만 순차로 되돌리려면 `NUTRITION_LOOKUP_CONCURRENCY`를 1로 바꾸면 됨. 테스트 중 동시성 2를 단언하는 1건은 함께 수정해야 함

## 8. Codex Grafana 측정 인수인계

Claude는 Grafana·Prometheus·k6 실측, 화면 캡처, 대시보드 JSON 편집, 전후 판정을 하지 않았음. 아래는 Codex가 그대로 실행할 수 있게 정리한 절차임.

### 8.1 비교할 코드 상태

| 상태 | 만드는 방법 |
|---|---|
| **After (병렬)** | 현재 작업 트리 그대로. HEAD `6d23bcd` + Codex 단계 계측 미커밋 변경 + 이번 patch |
| **Before (순차)** | 현재 작업 트리에서 `git apply -R phase5c-nutrition-parallel-only.patch`. 단계 계측은 남고 영양 조회만 순차로 돌아감 |

- patch 위치(동일 파일 두 곳):
  - `/private/tmp/claude-501/-Users-wooju-kang-Desktop-planB/43c006b7-c902-4420-8fd1-03aa8e8f44d8/scratchpad/phase5c-nutrition-parallel-only.patch`
  - `/Users/wooju-kang/Desktop/개발/Yeoro 프로젝트 모음/리팩토링 문서/텍스트 문서/phase5c-nutrition-parallel-only.patch` (권장. `/private/tmp`는 재부팅 시 사라질 수 있음)
- 변경 파일: `PlanService.java`, `PlanServiceTest.java`, `NutritionServiceTest.java`, `TravelLoadTestSmokeIntegrationTest.java`
- Before 측정 뒤 `git apply phase5c-nutrition-parallel-only.patch`로 After를 복원함. 각 상태를 적용한 직후 `git diff --stat`으로 상태를 확인하고 기록함
- 두 상태 모두 앱을 새로 띄워 측정함(같은 JVM에서 코드를 바꿀 수 없음)

### 8.2 필수 선행 작업: k6 동행인에 점심 설정 추가

현재 `src/test/k6/travel-plan.js`의 `setup()` 동행인은 `mealInfo.applied=false`임. 이 상태에서는 Java가 식사 슬롯을 만들지 않아 **영양 보강이 실행되지 않음**. 그러면 Grafana 측정에서 두 상태의 차이가 나타나지 않음.

- smoke 통합 테스트(`addLunchCompanion`)와 같은 조건이 필요함
  - `mealInfo = { applied: true, breakfastApplied: false, breakfastTime: null, lunchApplied: true, lunchTime: "12:00", dinnerApplied: false, dinnerTime: null }`
  - `healthInfo.diseaseTypes = ["DIABETES"]`, `walkType = "MINIMAL"`, `sensitiveAgree = true`
- 권장: 기본값을 바꾸지 않는 환경변수 옵션(예: `COMPANION_LUNCH=true`)으로 추가하고, `run-observed-load.sh`의 k6 전달 변수 목록에도 추가함. 기존 4A·5A·5B 비교 조건이 바뀌지 않게 하기 위함임
- 추가 후 먼저 smoke로 확인함
  - `SCENARIO=smoke`, `SMOKE_ITERATIONS=3`
  - 앱의 `planb_travel_plan_stage_seconds_count{stage="nutrition_lookup"}`가 요청당 2씩 증가하는지
  - 영양 stub 요청이 요청당 2건인지(비빔밥, 불고기)
- k6 `validPlan` check(하루 관광지 2개, `travelMinutes == 15`)가 점심 슬롯 추가로 실패하면, check를 약하게 바꾸기 전에 smoke 통합 테스트의 응답 구조와 비교해 원인을 기록함

### 8.3 스텁·앱 실행 조건

```bash
export PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"

docker run -d --rm --name planb-perf-mysql \
  -e MYSQL_DATABASE=planb_loadtest -e MYSQL_USER=planb -e MYSQL_PASSWORD=planb \
  -e MYSQL_ROOT_PASSWORD=planb-root -p 33306:3306 mysql:8.4
docker run -d --rm --name planb-perf-redis -p 36379:6379 redis:7.2

# OpenAI 호출당 3초, 영양 API만 4초 지연 (실제 API 표본 약 4초/건에 맞춤)
OPENAI_STUB_DELAY_MS=3000 \
STUB_SCENARIO_FOOD_NUTRITION=DELAY \
STUB_DELAY_MS=4000 \
./gradlew travelLoadTestStubs

SPRING_PROFILES_ACTIVE=loadtest \
DATABASE_URL=jdbc:mysql://localhost:33306/planb_loadtest \
DATABASE_USERNAME=planb DATABASE_PASSWORD=planb \
REDIS_URL=redis://localhost:36379 \
JWT_SECRET=01234567890123456789012345678901 \
./gradlew bootRun
```

- `STUB_DELAY_MS`는 `DELAY` 시나리오인 API에만 적용됨. 다른 외부 API는 `NORMAL`(지연 없음)
- 가상 스레드 설정은 두 상태에서 같게 둠. 작업 트리의 현재 설정을 확인해 기록하고 바꾸지 않음
- Before와 After마다 MySQL·Redis·스텁·앱을 새로 띄움
- 보조 조건으로 영양 250ms(`STUB_DELAY_MS=250`)를 같은 방식으로 반복하면 기존 로컬 측정과 비교할 수 있음

### 8.4 부하 실행과 TESTID

```bash
TESTID="phase5c-nutrition-<before|after>-<rate>rps-r<n>-$(date +%Y%m%d-%H%M%S)" \
SCENARIO=plan-throughput PLAN_RATE=<rate> PLAN_DURATION=60s \
PRE_ALLOCATED_VUS=<pre> MAX_VUS=<max> GRACEFUL_STOP=60s \
USER_COUNT=200 COMPANION_LUNCH=true \
src/test/observability/run-observed-load.sh
```

- 요청 1건 예상 시간: Before 약 14초, After 약 10초(§5)
- 권장 요청률·VU (예상 동시 요청의 2배 이상):
  - 1 req/s: pre 30 / max 60
  - 3 req/s: pre 90 / max 180
- 실행 순서: 각 상태에서 1 req/s를 warm-up 겸 1회 실행한 뒤, 3 req/s를 3회 반복함. Before 3회 → After 3회
- 각 실행 뒤 HTTP active, Hikari active·pending이 0으로 돌아온 것을 확인하고 다음을 시작함

### 8.5 측정 구간

- 시작: 해당 `testid`의 `max(timestamp(k6_http_reqs_total{testid=..., name=~"setup .*"}))`, 즉 setup 요청의 마지막 샘플. 계정 준비를 제외함
- 끝: `timestamp(k6_vus{testid=...})`의 마지막 값
  - remote write 시계열은 staleness marker가 없음. 값 자체로 잡으면 5분 뒤까지 이어지므로 timestamp를 사용함
- 실행 후 drain 수집 구간은 판정에서 제외함
- warm-up 실행(1 req/s)은 판정 표에 넣지 않음

### 8.6 비교 지표

| 지표 | 출처·쿼리 |
|---|---|
| 일정 생성 p50·p95 | k6 종료 요약 `plan_duration` (판정 기준). 보조로 서버 `http_server_requests_seconds_bucket{uri="/api/v1/travel/add-with-recommend"}` |
| 영양 보강 시간 | `planb_travel_plan_stage_seconds_sum/count{flow="create",stage="nutrition_enrichment"}`, histogram bucket으로 p95 |
| `nutrition_lookup` 시간·건수 | `planb_travel_plan_stage_seconds_*{flow="create",stage="nutrition_lookup"}`. outcome별(`success`/`failure`/`cancelled`) 건수 |
| 성공률·오류율 | k6 `plan_success`, HTTP 실패. 서버 `http_server_requests_seconds_count{outcome!="SUCCESS"}` |
| 영양 평가 결과 분포 | `planb_travel_nutrition_evaluation_total{status=...}` (`unavailable` 비율 비교) |
| Hikari | `hikaricp_connections_pending`, `hikaricp_connections_timeout_total`, `hikaricp_connections_acquire_seconds_max` |
| 외부 호출 수 | 일정 1건당 `http_client_requests_seconds_count{client_name="localhost"}` 증가량(외부 API 4개 합계), `okhttp_requests_seconds_count`(OpenAI). 영양 호출 수의 정확한 검증은 smoke 통합 테스트 단언으로 함 |
| 자원 | process CPU, heap, JVM thread |

- `planb.travel.plan.stage`의 SLO 경계는 loadtest 프로필에 5ms~300s로 설정돼 있음
- 대시보드에는 이 Timer 패널이 없음. JSON을 편집하지 않고 Prometheus 쿼리 결과를 저장해 판정함

### 8.7 판정 기준 (실행 전에 Codex 계획 문서로 고정할 것)

- **개선 확인**: After의 3 req/s 반복 3회 모두에서 `nutrition_enrichment` 평균이 Before보다 낮음. 일정 생성 p95가 Before보다 낮고, 차이가 반복 간 변동보다 큼
- **채택 불가**: 개선이 보여도 다음 중 하나라도 있으면 채택하지 않음
  - 일정당 외부 호출 수 차이
  - 성공률 하락 또는 오류 증가
  - `nutrition_lookup` `failure`·`cancelled` 증가
  - `unavailable` 비율 증가
  - Hikari pending·timeout 발생
  - 응답 일정의 메뉴·영양값 불일치(smoke 통합 테스트 실패 포함)
- 스텁 결과를 실제 공급자 처리량으로 일반화하지 않음. 실제 API에는 한도 탐색 부하를 보내지 않음

### 8.8 결과 보관

- 단계별 Prometheus 쿼리 결과 JSON, k6 원본 로그, Grafana 고정 시간 범위 링크와 캡처를 `testid`별로 보관함
- 저장소: `docs/perf/phase5c-nutrition-*`. 원본 로그와 캡처는 Git 밖 보관 디렉터리
- 기존 실험 JSON(`phase5c-*-2026100*.json`)은 수정하지 않음
- 원본 로그, API 키, `.env`, JWT는 문서와 Git에 넣지 않음

## 후속 Grafana 측정

2026-10-06 5A 트랜잭션 분리·5B 가상 스레드를 공통으로 적용한 뒤, 영양 조회만 순차(5B 기반 5C Before)·병렬(5C After)로 바꿔 로컬 3 req/s·4초 영양 스텁 조건에서 각 3회 측정. 일정 생성 p95 중앙값 14.17초에서 10.13초로 감소. 기존 5B 부하 실행은 식사 fixture가 달라 수치 비교에 사용하지 않음. 상세 지표·실행 조건·공급자 한도 미확인 사항은 [Grafana 결과](phase5c-nutrition-grafana-result.md) 참고.

운영 안전장치: `common-prod`의 `NUTRITION_LOOKUP_CONCURRENCY` 기본값은 1. 공급자 한도 확인 전에는 기존 순차 조회를 유지하며, 로컬 실험과 단위 테스트는 동시성 2.
