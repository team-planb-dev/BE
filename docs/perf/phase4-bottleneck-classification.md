# Phase 4 — Travel AI 병목 가설 판정

- Issue: #107
- 기준 commit: `40a9faf49f9a4344114d43e778ed45ebdbb509e1` (`origin/dev`)
- 판정일: 2026-09-29
- 범위: `POST /api/v1/travel/add-with-recommend`

## 1. 판정 기준

`확인`은 코드 사실이나 측정 결과가 가설을 직접 지지한다는 뜻이다. `기각`은 같은 조건에서
가설의 예측과 반대되는 결과가 반복됐다는 뜻이다. `증거 부족`은 코드상 가능성은 있지만 현재
측정이 적용 조건을 재현하지 못했다는 뜻이다.

이 판정에는 다음 자료를 사용했다.

- Phase 1 로그 계수: `docs/perf/log-observability-round1.md`
- Phase 2 결정적 OpenAI·외부 API 스텁
- Phase 3 기준선: `docs/perf/baseline-2026-09-28.md`
- 실제 LLM 품질 실행 `2026-09-28T00-35-17.979335Z`의 결과 요약
- 현재 `TravelFacade`, `PlanService`, `OpenAiClient`, `PlanTourismTool`의 호출 구조

실제 LLM 품질 실행은 12건 모두 `AI_RESPONSE_REJECTED`였으며 최소 4,334 ms, 중앙값
5,224 ms, 최대 10,513 ms가 걸렸다. 성공 요청의 운영 지연 분포로 사용할 수는 없지만,
동기 요청이 여러 초 동안 유지되는 현상은 확인할 수 있다.

## 2. 가설별 판정

| 후보 | 판정 | 근거 | Phase 5 결정 |
|---|---|---|---|
| P1 요청 스레드 장기 점유 | **증거 부족** | 실제 LLM 요청은 4.3~10.5초 동안 동기 요청을 유지했다. 그러나 모두 실패였고 thread·CPU·pool 시계열이 없다. 로컬 스텁은 5 requests/s에서 실제 VU 최대 1, 드롭 0이어서 플랫폼 스레드 포화를 재현하지 않았다 | 가상 스레드 실험 보류 |
| P2 중간 복구 상태 부재 | **확인** | Controller는 완성된 계획을 동기 응답하고 작업 ID나 복구 가능한 상태를 저장하지 않는다 | 성능 실험에서 제외. 별도 비동기 API·복구 계약 이슈 |
| P3 외부 호출 직렬 누적 | **증거 부족** | 실제 요청 표본은 Spring AI Tool 실행 로그 31회를 남겼지만 호출별 지연과 의존 관계가 없다. 현재 Tool 호출은 AI의 다음 판단에 입력되며, 독립 Java 조회가 전체 지연을 지배한다는 증거가 없다 | 병렬화 보류 |
| P4 동일 후보 반복 조회 | **증거 부족** | `PlanTourismTool`은 한 요청 안에서 같은 관광 지역 조회를 이미 재사용한다. 요청 간 정규화 키 반복률은 계측하지 않았다. 동일 입력만 반복한 부하 fixture는 운영 cache hit rate 근거가 될 수 없다 | 외부 조회 캐시 보류 |
| P5 로그 폭증 | **확인, 완화 완료** | 실제 요청 1건에서 174줄 중 Spring AI DEBUG가 125줄이었다. Phase 1에서 Spring AI를 INFO로 낮추고 외부 응답 전체 INFO 출력을 제거했다. 로컬 기준선은 정상 상태 25 lines/s, 최대 26 lines/s였다 | Phase 5 대상 아님 |
| P6 관측 수단 부재 | **확인, 완화 완료** | Actuator, Prometheus, `http.client.requests`, orchestration·retry·nutrition 지표와 관리 포트 격리가 추가됐다. Phase 3에서 orchestration, JVM, Hikari 지표를 읽었다 | Phase 5 대상 아님 |
| P8 외부 HTTP timeout 부재 | **확인** | Kor2Service, Kakao Map, Kakao Mobility의 `WebClient`에 connect·response timeout이 없다. `NutritionService.lookup()`만 별도 timeout을 가진다 | 별도 장애 격리 결함으로 수정 |

P1은 "요청이 오래 유지된다"는 하위 사실은 확인됐지만, 후보 A의 적용 조건인 "플랫폼 스레드가
CPU나 connection pool보다 먼저 포화된다"는 확인되지 않았다. 따라서 P1 전체를 `확인`으로
올려 가상 스레드를 적용하지 않는다.

P3의 Tool 호출은 이전 Tool 결과를 다음 AI 호출이 사용하는 orchestration이다. 호출 횟수가 많다는
이유만으로 독립 실행 가능한 Java 작업이라고 분류할 수 없다. waterfall이나 호출별 지연 없이
병렬화하면 candidate identity와 AI 선택 순서를 바꿀 위험이 있다.

## 3. DB와 Redis

두 저장소를 같은 병목으로 묶지 않는다.

### 3-1. DB connection pool

Phase 3의 5 requests/s 기준선 종료 snapshot은 두 번 모두 Hikari active 0, idle 10,
pending 0이었다. 측정한 부하에서는 DB pool 포화가 없었다.

이 결과는 실제 모델의 수 초 지연을 포함하지 않는다. `TravelFacade.makeTravelOptionsAndRecommend()`의
트랜잭션이 `PlanService.makePlanByAi()`를 감싸는 구조는 남아 있으므로, 실제 동시 요청에서
connection 점유가 늘어나는 위험은 **증거 부족**으로 유지한다. active·pending 시계열 없이
트랜잭션 경계를 변경하지 않는다.

### 3-2. Redis

Phase 3은 Redis command latency, connection과 pending command를 수집하지 않았다. 일정 생성에서
Redis는 사용자 cache 조회에 사용되지만 그 비중이나 포화는 측정하지 않았다. 따라서 Redis 병목도
**증거 부족**이다. DB 트랜잭션 경계와 묶어 수정하지 않는다.

## 4. 재시도 증폭

애플리케이션 correction과 SDK retry를 구분한다.

- `OpenAiClient`의 parse 또는 correction 재시도는 최대 1회이며 `planb.ai.retry`로 측정한다
- 결정적 OpenAI 스텁 테스트는 애플리케이션 재시도와 2차 실패 종료를 검증한다
- production 설정은 Spring AI/OpenAI SDK의 실제 HTTP attempt 횟수를 고정하거나 기록하지 않는다
- 실제 LLM 기준선은 Phase 1 지표 추가 전 commit에서 실행되어 retry timer가 없다

따라서 논리적 모델 호출 1회가 실제 OpenAI HTTP 요청 몇 회로 늘어나는지는 **증거 부족**이다.
애플리케이션 retry timer를 SDK retry 횟수로 해석하지 않는다. 이 값을 최적화하려면 transport
attempt를 직접 셀 수 있는 좁은 계측 또는 stub 시나리오가 먼저 필요하다.

## 5. Phase 5 진행 여부

Phase 5 후보 A~C의 적용 조건을 만족한 항목은 없다.

| 후보 | 결정 | 다시 검토할 증거 |
|---|---|---|
| A. 가상 스레드 | 보류 | 실제 지연을 포함한 동시 부하에서 플랫폼 스레드 포화, CPU 여유, pool 비포화가 함께 관측됨 |
| B. 외부 조회 캐시 | 보류 | 운영 정규화 키 반복률과 예상 hit rate, 후보 다양성 비회귀 기준이 확보됨 |
| C. 독립 조회 병렬화 | 보류 | 호출 waterfall에서 독립인 Java 조회가 전체 지연의 유의미한 비중을 차지함 |

현재 계획에 따라 확인되지 않은 최적화는 구현하지 않고 Phase 5를 종료한다. 다음 구현 대상은
성능 후보가 아니라 이미 확인된 P8 외부 HTTP timeout 결함이다. P2 비동기 작업화는 API 상태,
멱등성, 재시작 복구와 알림 계약을 함께 정해야 하므로 별도 V2 설계로 유지한다.

## 6. 남은 측정 한계

- Phase 3은 빠른 정상 스텁과 5 requests/s만 측정해 capacity나 breakpoint를 찾지 않았다
- CPU, memory, thread와 Hikari는 종료 snapshot이며 peak 시계열이 아니다
- 실제 LLM 실행은 전부 검증 실패여서 성공 경로 성능 분포가 없다
- 외부 의존성별 `http.client.requests` 지연은 기준선 문서에 보존하지 않았다
- Railway 운영 replica의 변경 후 peak logs/sec는 별도로 측정되지 않았다

이 한계는 결과를 과장하지 않기 위한 기록이다. 특정 후보의 적용 조건을 확인해야 할 때만 그
질문에 맞는 좁은 측정을 추가한다.
