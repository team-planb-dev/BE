# Phase 5C 일정 생성 구간 분석과 병렬화 판정

측정일: 2026-10-04
코드 기준: `main` `403b084405c1587028d82e7a655fdf3b0864d480` (#133 병합). #135 작업 트리 `6d23bcd`는 이 기준과 같은 운영 코드에 가상 스레드 기본값·keep-alive 설정만 추가한 상태.
증거: [보존한 집계값](phase5c-stage-metrics-20261004.json), #135 작업 트리의 `phase5b-main-comparison.md`와 `phase5b-main-results/`.

## 요청 흐름과 경계

| 순서 | Module / Interface → Implementation | 다음 단계와의 의존 | 계측 범위 | 병렬화 판단 |
|---|---|---|---|---|
| 1 | `TravelController` → `TravelFacade.makeTravelOptionsAndRecommend` | 사용자 ID 확인 | HTTP 요청 전체 | 요청 간 동시성은 5B의 대상. 요청 내부 후보 아님 |
| 2 | `TravelService.loadHealthContexts` → `TravelTransactionService.readOnly` | 선택 동행인의 건강 snapshot이 AI 입력 | 별도 시간 지표 없음 | AI와 병렬 불가. snapshot이 먼저 필요 |
| 3 | `PlanService.makePlanByAi` → `TravelRecommendHandler.createPlanByAi` → `OpenAiClient.call` | Prompt, Tool 후보, structured response 생성 | `planb.travel.ai.orchestration`, `spring.ai.chat.client`, `gen_ai.client.operation` | 모델의 다음 호출은 이전 Tool 결과를 소비. 현재 호출 순서 보존 |
| 4 | `TourismTool` / `PlanTourismTool` → Kor2·Kakao·영양 API | AI가 받은 후보와 `PlaceCandidateContext`, `NutritionEvaluationCollector` 갱신 | `spring.ai.tool`, `http.client.requests` | Tool 호출의 동적 순서와 공유 후보·ThreadLocal 수집기 때문에 일괄 fan-out 제외 |
| 5 | `PlanService.validatePlaces` | AI 결과와 후보 identity 검증·결손 복구 | orchestration에 포함, 단독 시간 없음 | 보정 결과가 다음 검증·route 입력. 일괄 병렬화 제외 |
| 6 | `PlanService.finishPlan` → route, 식사 보충, 복약·태그 | 확정된 이전 장소·종료 시간 및 최종 식사 슬롯 | orchestration에 포함, 단독 시간 없음 | route는 이전 슬롯에 의존. 식사 보충도 검증 후 실행 |
| 7 | `PlanService.enrichMissingNutritionEvaluations` | 최종 메뉴 확정 후 아직 평가되지 않은 메뉴 조회. 결과가 태그 입력 | 별도 시간 지표 없음. 기존 `planb.travel.nutrition.evaluation`은 Counter | 메뉴별 조회만 **조건부 후보**. 호출 수·지연·공급자 제한 측정 전 적용 보류 |
| 8 | `TravelService.saveGeneratedPlan` → `TravelTransactionService.write` | 최종 응답을 Travel부터 RestaurantDetail까지 원자적 저장 | orchestration 바깥, 별도 시간 지표 없음 | 완료된 일정이 입력이고 원자성 필요. 병렬화 제외 |

**Seam**은 `PlanService`의 AI 응답, 검증, 최종 보정 경계와 `TravelService`의 read/write 경계에 존재. `TravelFacade`는 이 순서를 조립하는 **Interface**. `OpenAiClient`와 외부 HTTP 클라이언트는 **Adapter**. 이 구조는 단계별 책임의 **Locality**를 지키면서, 병목이 입증된 구간 하나만 바꾸는 **Leverage**를 제공. 여러 단계의 내부 구현을 한꺼번에 교체하는 것은 Module의 **Depth**와 실패 계약을 흐림.

## 보존된 5B 실행에서 실제로 읽을 수 있는 시간

모든 실행은 로컬 OpenAI 스텁의 **모델 HTTP 호출당 고정 3초 지연**을 사용. 영양 스텁에는 지연 없음. 다음 수치는 각 실행 구간에서 Prometheus `increase(*_seconds_sum) / increase(*_seconds_count)`로 계산한 **완료 표본의 평균**. k6 전체 요청 p95와 같은 통계가 아님. `increase`는 스크레이프 구간을 외삽하므로 원시 count가 소수이고, 경계에 걸친 요청 때문에 k6 완료 건수와 다름.

| 실행 | k6 일정 생성 평균 / p95 | orchestration 평균 | ChatClient 평균 | 모델 호출 평균 | Tool 호출 평균 | orchestration − ChatClient 평균 |
|---|---:|---:|---:|---:|---:|---:|
| 플랫폼 1 req/s | 6.14 / 6.17초 | 6.058초 | 6.035초 | 3.012초/호출 | 8.0ms/호출 | 약 23.5ms |
| 플랫폼 3 req/s | 6.11 / 6.14초 | 6.045초 | 6.029초 | 3.011초/호출 | 5.4ms/호출 | 약 16.0ms |
| 플랫폼 36 req/s | 8.26 / 10.05초 | 6.037초 | 6.021초 | 3.008초/호출 | 4.0ms/호출 | 약 15.9ms |
| 가상 36 req/s | 6.06 / 6.12초 | 6.018초 | 6.012초 | 3.005초/호출 | 1.7ms/호출 | 약 6.6ms |

`TravelLoadTestSmokeIntegrationTest`는 생성 요청의 OpenAI 스텁 호출 **정확히 2회**를 검증. 따라서 현재 스텁 시나리오에서는 두 모델 호출의 고정 지연 약 6초가 요청 시간의 대부분. 36 req/s 플랫폼 모드에서 k6 평균과 orchestration 평균의 차이가 큰 현상은 Tomcat busy 200/200·시작 누락 42건과 함께 요청 대기를 시사하지만, 이 둘을 빼서 정확한 큐 대기시간으로 해석할 수는 없음.

가상 36 req/s 구간에서 `http.client.requests`는 외부 API 4개가 모두 localhost로 합쳐진 지표이며, 합계 약 5.14초/집계 13,838호출. `spring.ai.tool`은 관측된 `searchAttractionsByRegion` 호출의 평균 약 1.7ms이며 ChatClient 안의 하위 관측. 반면 `http.client.requests`에는 Tool 내부 조회와 ChatClient 반환 뒤 `finishPlan`의 경로·영양 조회가 함께 들어갈 수 있음. 이 집계값을 ChatClient 시간에 더하거나 특정 단계의 시간으로 해석하지 않음. 외부 API별 실제 지연도 뜻하지 않음. 조회 snapshot, Java validation, route, 영양 보강, 최종 DB 저장은 각각의 전용 시간 지표가 없어 별도 ms 또는 p95를 제시할 수 없음. 가상 36 req/s의 영양 평가 Counter 시계열도 없으므로 이 실행으로 영양 보강의 효과를 판정하지 않음.

## 병렬화 결정

**현재 결정: Phase 5C 요청 내부 병렬화의 운영 적용 보류.** 두 메뉴를 보강하는 경로에서 실제 API 호출은 약 4초/건이고, 4초 지연 fixture의 영양 보강은 전체 요청 시간의 약 56.6%. 따라서 이 구간은 성능 후보로 확인. 다만 공급자의 계정별 초당·동시 호출 허용 수가 확인되지 않았고, 실제 응답 중 `불고기`는 현재 필터 결과가 0건. 동시 2건 단발 성공만으로 다중 사용자 부하의 외부 호출 예산·품질 보존을 증명할 수 없어 운영 병렬화는 아직 적용하지 않음.

첫 조건부 후보는 최종 식사 슬롯 이후의 메뉴별 영양 조회. `NutritionEvaluationCollector.finish()` 뒤에 실행되어 Collector의 ThreadLocal에 결과를 다시 쓰지 않고, 메뉴마다 입력이 독립적임. 다만 `evaluatedMenus`의 중복 제거를 fan-out 전에 완료해야 하며, 메뉴 순서, HTTP 조회별 15초 timeout, 실패 시 `UNAVAILABLE`, 외부 quota를 보존해야 함. 이 후처리 경로는 `standardFoodName=null`을 전달하므로 표준명 재조회는 수행하지 않음. Tool 경로의 재조회 정책과 혼동 금지. 별도 실측에서 이 루프가 전체 p95에 반복적으로 의미 있게 기여할 때만 작은 동시성 상한의 순서 보존 병합을 검토. `Flux.flatMapSequential`은 동시 구독과 원래 순서의 결과 병합을 지원하지만, 적용 결정의 근거는 API 기능이 아니라 실제 latency와 호출 안전성임.

## 단계 계측 구현 상태

Phase 5C 계측 작업 트리에서 기존 `planb.travel.ai.orchestration`을 유지하고 `planb.travel.plan.stage` Timer를 추가. 고정 태그는 `flow`(`create`/`edit`), `stage`, `outcome`(`success`/`failure`, 영양 조회 취소는 `cancelled`)뿐이며 사용자·메뉴 식별자는 기록하지 않음.

| flow | stage | 측정 경계 |
|---|---|---|
| create | `health_snapshot` | 건강정보 readOnly 트랜잭션 |
| create | `ai_response` | AI 및 Tool 응답 |
| create | `place_validation` | 장소 후보 검증 |
| create | `finish_plan` | 최종 보정 전체. 아래 영양 구간을 포함 |
| create/edit | `nutrition_enrichment` | 누락 메뉴 영양 평가 전체 |
| create/edit | `nutrition_lookup` | 중복 제거된 메뉴 1개당 영양 조회 |
| create | `persistence` | 최종 write 트랜잭션과 응답 조립 |

실패한 단계도 `finally`에서 기록. `finish_plan`과 `nutrition_enrichment`·`nutrition_lookup`은 포함 관계이므로 합산 금지. 기존 편집 경로는 `nutrition_enrichment`와 `nutrition_lookup`만 계측하며, 나머지 생성 전용 stage와 비교하지 않음. loadtest 프로필은 단계 Timer의 SLO 경계를 5ms~300초로 설정.

단위 테스트에서 생성·편집 태그, 실패 outcome, 중복 메뉴 1회 조회 및 서로 다른 메뉴 2회 조회를 검증. 로컬 스텁 통합 테스트에서 생성·저장 단계의 Timer 증가를 확인. 이는 계측 검증이며 병렬화의 성능 개선 증거가 아님.

영양 조회 병렬화 이후 `nutrition_lookup` Timer는 조회 구독부터 메뉴별 완료까지 측정. 같은 요청의 두 조회가 겹치므로 조회 시간의 합계는 `nutrition_enrichment` wall-clock보다 클 수 있음. 다른 메뉴의 예외로 취소된 조회는 `cancelled` outcome.

## 남은 측정과 판정

기존 5B 부하 테스트의 OpenAI fixture `travel-empty-plan.json`만으로는 최종 식사 메뉴의 영양 보강을 보장할 수 없어서, 그 실행으로 영양 조회 지연이나 병렬화 효과를 판단할 수 없음. 아래 5C 통합 fixture는 점심 건강정보와 두 음식점 후보를 사용해 서로 다른 메뉴 2개와 영양 HTTP 호출 2건을 검증한 뒤 고정 지연을 바꿔 반복 측정. 기존 코드 주석의 외부 영양 API 4~6초 지연은 이 시점까지 가설이었고, 아래 실제 표본에서 준비 호출 뒤 약 4초/건을 관측.

로컬 fixture와 실제 API의 소수 표본으로 영양 조회의 지연 기여도는 확인. 운영 병렬화 결정에는 공급자의 계정별 초당·동시 호출 허용 수, 다중 사용자 구간의 오류율, 병렬 변경 전후 전체 요청 p95가 추가로 필요. 측정하지 않은 수치를 기본값으로 가정하지 않음.

## 식사 fixture 기반 로컬 요청 실험

`TravelLoadTestSmokeIntegrationTest.measuresDelayedNutritionLookupsInTravelCreation`에서 1박 2일, 점심 12시가 등록된 동행인 1명, 로컬 Kor2 음식점 후보와 OpenAI 빈 일정 fixture를 사용. Java가 이틀의 점심 슬롯을 비빔밥·불고기로 보충하고 `FOOD_NUTRITION` 스텁에 **서로 다른 메뉴 2건**을 조회하는 계약을 검증. OpenAI와 식품영양 API의 지연은 각 HTTP 응답별 고정값. 표의 `request`는 회원가입·로그인·동행인 등록을 제외한 `/api/v1/travel/add-with-recommend` MockMvc 호출 시간. 외부 유료 API 호출 없음.

| OpenAI 지연/호출 | 영양 API 지연/호출 | 요청 전체 | orchestration | 영양 보강 | 영양 조회 합계 | 조회 수 |
|---:|---:|---:|---:|---:|---:|---:|
| 0ms | 0ms | 98.55ms | 43.8ms | 3.2ms | 3.2ms | 2 |
| 0ms | 250ms | 632.05ms | 573.4ms | 531.8ms | 531.7ms | 2 |
| 3,000ms | 0ms | 6,148.95ms | 6,096.2ms | 3.5ms | 3.5ms | 2 |
| 3,000ms | 250ms | 6,698.05ms | 6,622.1ms | 530.6ms | 530.5ms | 2 |

각 조건을 같은 JVM에서 **3회** 반복. 첫 실행은 클래스·연결 초기화의 영향이 커서 비교에서는 제외하고, 표는 **2·3회차 중앙값**. 모든 실행에서 서로 다른 메뉴 2개와 영양 HTTP 요청 2건 확인. [원시 12개 측정값](phase5c-nutrition-local-measurements-20261004.json)에 첫 실행을 포함한 전체 결과 보존. 조건별 비교 표본이 2개뿐이므로 p95·신뢰구간·운영 환경 추정치가 아님.

OpenAI 호출당 3초 조건에서 영양 응답당 250ms를 추가하면 요청 전체 중앙값이 **6,148.95ms → 6,698.05ms**, 약 **549.1ms 증가**. 후자의 영양 보강 중앙값은 **530.6ms**, 요청 전체의 약 **7.9%**. OpenAI 지연이 0일 때 같은 영양 지연 추가로 요청 전체 중앙값은 **98.55ms → 632.05ms**, 약 **533.5ms 증가**. 두 조회의 순차 대기가 전체 요청에 반영됨을 이 로컬 fixture로 확인. 이 표 자체는 실제 API 지연·허용 한도·병렬 적용 후 전체 요청 성능을 나타내지 않음. 실제 API 소수 표본은 아래에 별도 기록.

`FoodNutritionLatencyExternalTest`는 실제 API 키가 준비된 환경에서 순차 3회 호출과 준비 호출 뒤 동시 2건을 각각 측정. 순차 호출은 2초 간격으로 API 응답 코드·본문 존재 여부·호출별 시간을 기록. HTTP 오류·타임아웃은 경과 시간·HTTP 상태(가능한 경우)·실패 종류를 기록하고 추가 호출 없이 중단. 이 표본은 실제 Handler의 HTTP·파싱 구간을 측정하며, `NutritionService`의 후처리나 실제 일정 생성 전체 시간은 측정하지 않음. 동시 호출 한도를 찾기 위한 부하 실험은 하지 않음. 공식 공공데이터포털은 일일·초당 초과 오류를 명시하지만 초당 허용 수치·동시 호출 수·응답시간 SLA는 공개하지 않음. 세부 출처와 실행 조건은 [식품영양 API 공식 자료 확인](phase5c-nutrition-api-research.md)에 기록.

```bash
./gradlew externalTest --tests 'com.planb.integration.external.foodNtrCpnt.FoodNutritionLatencyExternalTest' --info --rerun-tasks
```

필요 환경변수: `FOOD_NTR_CPNT_URL`, `FOOD_NTR_CPNT_KEY`. 키 값이나 키가 포함된 URL은 결과 기록에서 제외. Gradle이 테스트를 `UP-TO-DATE`로 건너뛸 수 있으므로 실제 표본 실행에는 `--rerun-tasks`를 추가. 이 작업의 실제 실행은 로컬 `.env`에서 변수를 읽되 키를 출력하지 않는 방식으로 완료.

## 실제 식품영양 API 표본과 요청 기여도

2026-10-05 실제 서비스에 현재 `FoodNtrCpntHandler.searchFoodNutrition` 요청을 사용. 동일 메뉴들의 **순차 3회**(처음 1회는 연결 준비의 영향 가능)와, 준비 호출 뒤 **동시 2건을 각각 1회씩** 수행. 두 동시 실행 모두 API 응답 코드 `00`이고 HTTP 오류·초당 제한 오류는 관측되지 않음. [비밀값 없는 원시 표본](phase5c-real-nutrition-measurements-20261005.json)에 숫자 보존.

| 실행 | 메뉴별 시간 | 조회 2건 비교값 | 해석 |
|---|---:|---:|---|
| 순차 준비 후 2건 | 불고기 4,045.4ms + 비빔밥 4,054.0ms | HTTP 시간 합계 8,099.4ms | 호출 사이 2초 휴지 제외 |
| 동시 2건, 첫 표본 | 비빔밥 4,062.0ms / 불고기 4,034.7ms | 동시 묶음 wall-clock 4,071.4ms | 두 HTTP 대기의 중첩 |
| 동시 2건, 두 번째 표본 | 비빔밥 4,151.4ms / 불고기 4,147.2ms | 동시 묶음 wall-clock 4,156.9ms | 반복 표본에서도 중첩 |

첫 순차 호출은 **8,180.2ms**로 이후 호출보다 길었으므로 준비 영향 가능성이 있음. 동시 2건 두 차례의 성공은 **동시 2건을 잠깐 처리할 수 있었다는 관찰**이며, 제공자가 허용하는 지속 초당 요청 수나 계정 전체 동시성 상한의 증거가 아님. 공개 자료에도 수치가 없으므로 rate-limit 오류 `23`을 유도하는 부하 실험은 수행하지 않음.

동일한 4초/건 지연을 로컬 여행 생성 fixture에 넣자, 준비 회차를 제외한 요청 전체 중앙값은 **14,190.5ms**, `nutrition_enrichment` 중앙값은 **8,024.75ms(약 56.6%)**. 앞선 0ms 영양 지연 조건의 요청 중앙값 **6,148.95ms**와 약 **8,041.55ms** 차이. 따라서 메뉴 두 건의 외부 대기가 완료 응답의 큰 부분을 차지할 수 있음. 순차 개별 HTTP 시간의 합계 약 8.1초와 동시 묶음 wall-clock 약 4.1초의 차이를 전체 요청에 대입하면 최대 약 4초 절감 여지가 있지만, 이는 **추정**이며 운영 전체 p95 개선을 측정한 결과가 아님.

실제 응답 품질은 별개. 현재 `FoodNtrCpntHelper.filterFoodNutrition`으로 재검사한 두 번째 동시 표본에서 `비빔밥`은 3건, `불고기`는 0건 매칭. `불고기`는 API 응답 자체가 성공해도 현재 `NutritionService`에서 `UNAVAILABLE` 평가로 이어질 수 있음. 데이터 매칭 오류를 병렬화로 해결할 수 없으며, 외부 호출량만 앞당길 수 있음. 실제 모든 여행 생성에서 보강이 실행되는 것도 아님: Tool 평가가 이미 있는 메뉴는 이 후처리 대상에서 제외.

## 병렬 변경 시 보존할 계약

- `evaluatedMenus`의 중복 제거를 fan-out **이전**에 완료하고 메뉴별 조회 수 유지. `flatMapSequential` 등으로 원래 메뉴 순서 보존 후 `enriched`에 합침
- 조회별 15초 timeout, 메뉴별 `UNAVAILABLE` fallback, 평가 Counter 및 실패 지표 보존. 한 메뉴 실패가 다른 메뉴 성공을 숨기거나 성공 응답을 지연시키지 않는지 검증
- `NutritionEvaluationCollector`의 `ThreadLocal` 수집은 AI Tool 단계 내부에만 유지. 후처리 루프만 대상으로 하고 요청 간 상태 공유 금지
- 요청당 동시성 2만으로 공급자 제한을 보장할 수 없음. 다중 사용자 상황의 전체 초당 호출량·동시 호출량을 계정 승인 한도와 대조하고, 필요 시 애플리케이션 전체 상한을 먼저 설계
- 식사·복약·경로·저장 단계의 순서와 최종 일정 품질 유지. 변경 전후 동일 fixture의 전체 p95·외부 호출 수·`23`/timeout·`UNAVAILABLE` 비율 비교

**판정:** 영양 후처리의 직렬 외부 대기는 5C의 의미 있는 병렬화 후보로 확인. 운영 적용은 계정별 호출 제한과 매칭 품질 확인 후 별도 변경·회귀 측정으로 진행. 이번 변경은 계측과 실험 테스트까지.

공식 근거: [Micrometer Timer.Sample](https://docs.micrometer.io/micrometer/reference/concepts/timers.html), [Spring AI 관측 범위](https://docs.spring.io/spring-ai/reference/observability/), [Spring AI Tool 관측](https://docs.spring.io/spring-ai/reference/api/tools.html), [Reactor `flatMapSequential`](https://projectreactor.io/docs/core/release/api/reactor/core/publisher/Flux.html#flatMapSequential(java.util.function.Function,int)).

## 후속 측정

2026-10-06 5A 트랜잭션 분리와 5B 가상 스레드 구조를 공통으로 두고, 영양 조회만 순차(5C Before)·병렬(5C After)로 바꿔 동일한 3 req/s·4초 영양 스텁 조건에서 각 3회 비교. 일정 생성 p95 중앙값 14.17초에서 10.13초로 감소. 기존 5B 부하 결과는 영양 조회 조건이 달라 이 개선율의 직접 비교값이 아님. 요청당 외부 호출 2건과 결과 상태 동일. 상세 결과는 [Phase 5C 영양 조회 병렬화 전후 부하 측정](phase5c-nutrition-grafana-result.md) 참고. 실제 제공자 전체 한도는 미확인. `common-prod`는 동시성 기본값 1로 기존 순차 조회 유지.
