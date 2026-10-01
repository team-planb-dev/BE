# Phase 4A-2 공식 근거 검토

검토일: 2026-09-30. 대상: `/private/tmp/planB-codex-117`의 실험 초안, loadtest 설정, OpenAI 스텁, k6 스크립트와 Grafana dashboard. 코드 변경 및 부하 실행 없이 검토했다. 프로젝트는 Spring Boot 4.0.2, Spring AI 2.0.0, Java 21이며 Micrometer 1.16.2와 Spring AI 2.0.0의 로컬 Gradle 캐시에 있는 공식 sources JAR도 직접 확인했다.

## 0. 초안 검토 후 반영 상태

이 문서의 위험 서술은 첫 초안을 기준으로 한다. 최종 실행 계약에는 다음을 반영했다.

- Tomcat connector를 합산하지 않고 `name`별로 표시하며 8080 connector만 판정한다.
- 1 req/s와 3 req/s의 VU를 각각 10/20, 24/60으로 나누고, Tomcat 탐색은 DB pool이 포화되지 않을 때만 200/260 VU로 실행한다.
- `GRACEFUL_STOP`을 30초 또는 60초로 전달하고, k6 종료·중단 후 30초 동안 scrape를 유지한 뒤 JWT 파일을 삭제한다.
- loadtest에서 `spring.ai.openai.timeout=20s`, `max-retries=0`을 명시했다.
- 실행 전 live Grafana URL과 일정 생성 HTTP 5xx·timeout 전용 패널을 추가했다.
- busy/max 0.8은 높은 사용률일 수 있으므로 처리량 정체, 지연 증가 또는 drop을 함께 요구한다.

## 1. Tomcat MBean과 지표

`server.tomcat.mbeanregistry.enabled: true`는 Boot 4.0에서 유효하다. 기본값은 `false`이며 Tomcat 계측에는 MBean Registry가 필요하다. 현재 `application-common-loadtest.yml`에만 켠 범위는 실험 목적에 맞는다. 이 설정 자체가 앱의 가상 스레드를 활성화하거나 remote JMX 포트를 여는 설정은 아니다. [Boot 4.0 metrics](https://docs.spring.io/spring-boot/4.0/reference/actuator/metrics.html#actuator.metrics.supported.tomcat), [Boot 4.0 properties](https://docs.spring.io/spring-boot/4.0/appendix/application-properties/index.html)

Micrometer `TomcatMetrics.bindThreadPoolMetrics`는 `ThreadPool` MBean에서 다음 gauge를 만들며 base unit은 `threads`다. 현재 dashboard의 Prometheus 이름은 이에 맞다. [공식 Micrometer 1.16.2 sources JAR](https://repo.maven.apache.org/maven2/io/micrometer/micrometer-core/1.16.2/micrometer-core-1.16.2-sources.jar), 내부 `io/micrometer/core/instrument/binder/tomcat/TomcatMetrics.java:143–163`

| Micrometer | Prometheus | MBean attribute |
|---|---|---|
| `tomcat.threads.busy` | `tomcat_threads_busy_threads` | `currentThreadsBusy` |
| `tomcat.threads.current` | `tomcat_threads_current_threads` | `currentThreadCount` |
| `tomcat.threads.config.max` | `tomcat_threads_config_max_threads` | `maxThreads` |

**초안에서 확인한 판정 위험:** 최초 dashboard는 세 지표를 connector 구분 없이 `sum`했다. 최종 dashboard는 connector를 `name`별로 표시하며 application 8080 connector만 판정한다. 실제 scrape에서도 series label과 max 실측값을 보존한다.

**초안에서 확인한 실험 범위 한계:** Boot 기본 `server.tomcat.threads.max`는 200이다. 최초 `maxVUs=40`으로는 busy/max 0.8, 즉 busy 160에 접근할 수 없었다. 최종 계약은 Hikari에 여유가 있을 때만 200/260 VU 조건으로 Tomcat을 탐색한다. 실제 max 또는 다른 부하가 다르면 다시 계산한다. [Boot 4.0 properties](https://docs.spring.io/spring-boot/4.0/appendix/application-properties/index.html)

## 2. k6 1 rps / 3 rps 조건

`constant-arrival-rate`는 응답 완료와 독립적으로 iteration 시작을 예약하는 open model이다. `timeUnit: '1s'`의 rate 1/3은 초당 iteration 시작 1/3회다. `createPlan`은 일정 POST 한 번이므로 workload에는 일치하지만 setup·teardown HTTP 요청까지 포함한 `http_reqs`는 별도로 필터링해야 한다. `iterations`는 완료량으로 해석하며, 목표 arrival rate와 같은 뜻으로 쓰지 않는다. [k6 executor](https://grafana.com/docs/k6/latest/using-k6/scenarios/executors/constant-arrival-rate/), [k6 built-in metrics](https://grafana.com/docs/k6/latest/using-k6/metrics/reference/)

공식 VU 추정식은 iteration duration × arrival rate + 여유분이다. 두 번의 3초 호출만으로 약 6초이므로 저율은 약 6 VU, 고율은 약 18 VU에 추가 여유가 필요하다. 초안의 `preAllocatedVUs=10`은 고율에서 동적 할당을 전제로 한다. Grafana는 실행 도중 VU 생성의 CPU·메모리 비용이 결과를 왜곡할 수 있어 충분한 사전 할당을 권한다. `maxVUs=40`에서는 평균 지연이 약 13.3초를 넘으면 3 rps 유지가 어려워진다는 계산상 한계가 있다. [k6 VU allocation](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/arrival-rate-vu-allocation/)

`dropped_iterations`는 arrival executor가 VU 부족으로 시작하지 못한 iteration 수다. 서버 HTTP 오류와 구분하고, 예정 시작 수·실제 시작/완료 수·drops·중단 수를 함께 남긴다. drop 0이라고 장기 안정성을 증명하지는 않는다. [k6 built-in metrics](https://grafana.com/docs/k6/latest/using-k6/metrics/reference/)

**초안에서 확인한 종료 위험:** 최초 `gracefulStop: '10s'`에서는 포화 때 남은 요청이 `planDuration.add`와 `planSuccess.add`에 도달하기 전에 강제 중단될 수 있었다. 최종 실행은 30초 또는 60초를 전달하고, interrupted 수와 서버 후속 작업을 함께 보존한다. [k6 graceful stop](https://grafana.com/docs/k6/latest/using-k6/scenarios/concepts/graceful-stop/)

## 3. Prometheus remote write 해석

공식 매핑은 Counter → `k6_*_total`, Rate → gauge `k6_*_rate`이고, trend 통계는 `_p50`, `_p95`, `_p99`, `_avg`, `_max` 등으로 출력한다. 현재 `K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),p(99),avg,max`와 dashboard 조회는 일관된다. percentile gauge를 합산하거나 평균하여 전체 percentile로 해석하면 안 된다. 기본 push는 5초이고 stale marker 기본값은 false다. 종료 뒤 마지막 값이 남을 수 있으므로 각 실행 고유 testid와 정확한 시간 범위를 사용한다. [k6 Prometheus remote write](https://grafana.com/docs/k6/latest/results-output/real-time/prometheus-remote-write/)

`phase4a-observability.md`의 기존 실행은 custom time trend `k6_plan_duration_p95`가 초 단위인 것을 확인했다. 이는 해당 고정 k6 버전의 실측 근거이며 현재 dashboard의 `s` 단위와 일치한다. 새 실행에서도 실제 series 이름과 단위를 확인한다. remote-write는 experimental이므로 현재 이미지 digest 고정을 유지한다. [k6 remote write 안정성 설명](https://grafana.com/docs/k6/latest/results-output/real-time/prometheus-remote-write/)

**판정 위험:** 현재 `1 - k6_plan_success_rate`는 응답 본문 검증 실패도 포함한다. 이것을 초안의 “5xx 또는 timeout 20%”로 대체해서는 안 된다. 또한 Rate/percentile gauge는 원시 사건 counter가 아니므로 짧은 구간 오류율은 상태별 HTTP counter 등의 증가량으로 따로 계산해야 한다. 현재 script의 threshold는 종료 판정뿐이며 CPU·pending·15초 지속 조건을 감시해 자동 중단하는 코드는 없다. 수동 감시 책임과 계산 구간을 사전 고정해야 한다. [k6 metric types](https://grafana.com/docs/k6/latest/using-k6/metrics/), [k6 remote write 매핑](https://grafana.com/docs/k6/latest/results-output/real-time/prometheus-remote-write/)

초안의 `run-observed-load.sh`는 종료 6초 뒤 scrape JWT 파일을 삭제해 지연 요청의 후속 지표가 끊길 수 있었다. 최종 스크립트는 종료·중단 후 30초 drain하고 반복 중단 신호에도 JWT를 삭제한다. 다음 실험 전 Hikari active/pending과 요청 처리가 내려온 것을 확인한다. 앱 지표에는 k6 testid가 없으므로 Grafana 시간 범위로 상관관계를 맞춘다.

## 4. Java 지연 스텁

`HttpServer.setExecutor`는 `start` 전에 지정해야 하며 모든 HTTP 요청을 해당 executor의 task로 처리한다. 현재 순서는 맞다. executor를 생략하면 기본 서버 thread가 사용되므로 느린 handler를 직렬화하는 교란이 생길 수 있다. [Java 21 HttpServer](https://docs.oracle.com/en/java/javase/21/docs/api/jdk.httpserver/com/sun/net/httpserver/HttpServer.html#setExecutor(java.util.concurrent.Executor))

`newVirtualThreadPerTaskExecutor()`는 task마다 가상 스레드를 만드는 무제한 executor다. 현재 스텁은 잠들기 전에 요청 저장의 synchronized 구간을 빠져나오므로 3초 sleep을 전역 lock 안에서 직렬화하지 않는다. 이것은 스텁의 동시 처리 구성일 뿐 앱 Tomcat의 가상 스레드 실험이 아니다. [Java 21 Executors](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/Executors.html#newVirtualThreadPerTaskExecutor())

`Thread.sleep(Duration)`는 시스템 timer와 scheduler의 정확도에 영향을 받는다. 3000ms는 주입하는 목표 대기이며 HTTP 왕복이 정확히 3000ms라는 보장은 아니다. request body 읽기·응답 작성·스케줄링 시간이 추가된다. InterruptedException 후 interrupt 복원과 exchange 종료는 현재 구현에서 수행한다. [Java 21 Thread.sleep](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/lang/Thread.html#sleep(java.time.Duration))

최종 `OPENAI_STUB_DELAY_MS`는 OpenAI 정상 응답에만 적용되며 외부 API 전체의 3초 지연을 뜻하지 않는다. `requests`는 전체 prompt를 계속 보관하고 executor도 무제한이므로 짧은 로컬 실험에는 적합하지만 무기한 soak 테스트 용량 보장은 없다. `server.stop(0)` 후 `executor.close()`는 진행 task 종료까지 기다릴 수 있으므로 중단 즉시 모든 지연 task가 취소된다고 가정하지 않는다. [Java ExecutorService.close](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/util/concurrent/ExecutorService.html#close())

## 5. Spring AI timeout / retry

Spring AI 2.0.0 공식 sources의 `AbstractOpenAiOptions` 및 `AbstractOpenAiProperties` 기본값은 timeout 60초, maxRetries 3이다. 공식 설정 키는 `spring.ai.openai.timeout`, `spring.ai.openai.max-retries`이고 chat-specific 설정은 common 설정 해석 시 우선 적용될 수 있다. `OpenAiSetup`은 이를 실제 HTTP client와 SDK client options에 전달한다. 초안에는 두 값을 명시하지 않았지만 최종 loadtest는 timeout 20초, maxRetries 0으로 고정한다. [Spring AI OpenAI 문서](https://docs.spring.io/spring-ai/reference/api/chat/openai-chat.html), [모델 2.0.0 sources](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-openai/2.0.0/spring-ai-openai-2.0.0-sources.jar), [자동 설정 2.0.0 sources](https://repo.maven.apache.org/maven2/org/springframework/ai/spring-ai-autoconfigure-model-openai/2.0.0/spring-ai-autoconfigure-model-openai-2.0.0-sources.jar)

실험에서 timeout을 3초 이하로 낮추면 요청 timeout/재시도가 생겨 “일정당 2회 × 3초” 가정이 깨질 수 있다. `spring.ai.retry.max-attempts` 같은 구형 retry 설정을 현재 SDK 경로의 제어라고 가정하지 않는다. transport retry를 통제하려면 loadtest의 실제 `spring.ai.openai.max-retries`를 고정하고, 기본 60초 또는 충분한 여유 timeout을 기록한다. 앱의 `OpenAiClient` correction retry는 별도 계층이므로 `planb.ai.retry`도 관측한다. `OpenAiProtocolStubTest`의 모델 fixture는 timeout 2초 / maxRetries 0이므로 그대로 3초 지연 integration 검증에 재사용하면 timeout이 발생한다.

## 6. 실험 결론의 경계

`TravelFacade.makeTravelOptionsAndRecommend`는 `@Transactional` 내부에서 DB 접근·저장을 먼저 하고 AI를 호출한다. 따라서 connection을 AI 왕복 중 보유한다는 가설은 코드 흐름과 일치하지만, `10 / 6 ≈ 1.67 req/s`는 연결 점유 시간이 6초라는 조건부 근사다. 실제 max pool, usage time, pending, 성공 응답당 OpenAI 왕복 수로 확인해야 한다. tool·DB·직렬화 시간이 추가되므로 정확한 처리량 상한으로 표기하지 않는다.

우선 확인할 항목은 충분한 VU 사전 할당, application connector별 Tomcat 비율, transport retry/timeout 고정, 종료 후 drain, 중단 조건의 실제 감시 방법이다. 이 확인을 마친 1/3 rps 실행에서 DB active 10과 pending이 먼저 증가하면 해당 조건에서 DB connection 보유가 선행 제약이라는 근거가 된다. Tomcat이 포화되지 않았다는 사실은 이 부하 범위에 한정한다.
