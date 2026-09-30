# Phase 4A-1 — 로컬 Prometheus·Grafana 부하 관측

- Issue: #112
- 기준 commit: `eb12438` (`origin/dev`)
- 범위: 로컬 부하 측정에서 애플리케이션 지표와 k6 결과를 **같은 시간축**으로 수집·보존·시각화한다.
  P1·DB pool 재판정(Tomcat 지표, OpenAI 고정 지연)은 Phase 4A-2다

## 1. 구성

```text
src/test/observability/
├── docker-compose.yml                 Prometheus + Grafana (이미지 다이제스트 고정, 127.0.0.1에만 노출)
├── prometheus/prometheus.yml          scrape 2초, JWT 파일 인증
├── grafana/provisioning/              datasource, 대시보드 폴더
├── grafana/dashboards/travel-load.json  패널 14개
├── scrape-token.sh                    임시 JWT 파일 생성·삭제
└── run-observed-load.sh               위를 묶어 k6를 remote write로 실행
```

`application-common-loadtest.yml`에 SLO 버킷을 추가했다. 운영 Java 코드와 다른 프로파일 설정은 바꾸지 않았다.

## 2. 수집 계약

| 항목 | 값 |
|---|---|
| scrape 대상 | `host.docker.internal:8081/actuator/prometheus` |
| scrape 간격·timeout | 2초 / 2초. 30초 부하에서도 시계열이 나오게 한 값이다. `rate()` 구간은 10초를 쓴다 |
| 인증 | `authorization.credentials_file`(type 기본값 Bearer)에 raw JWT. `JwtFilter`가 `Bearer ` 접두사를 요구한다 |
| retention | 2일. 지표는 named volume에 남고 `down -v`로 지운다 |
| 노출 | Prometheus 9090, Grafana 3000 모두 `127.0.0.1`에만 바인딩한다 |

관리 포트는 사용자 JWT를 요구하고 액세스 토큰은 24시간 뒤 만료된다(`ACCESS_TOKEN_EXPIRED_MS`). 그래서 이 방식은 **짧은 로컬
측정에만** 쓴다. 상시 수집 인증은 별도 Issue다.

### 2-1. 임시 JWT 수명

1. 앱 기동 뒤 `scrape-token.sh create`가 테스트 계정을 만들고 로그인한다
2. 응답의 `Bearer ` 접두사를 뗀 raw JWT만 임시 디렉터리에 `0600`으로 쓴다. 토큰은 어떤 출력에도 남기지 않는다
3. compose가 그 디렉터리를 Prometheus에 읽기 전용으로 붙인다
4. `run-observed-load.sh`가 **종료할 때**(성공, 실패, 중단) `trap`으로 파일과 디렉터리를 지운다

확인한 경로: 정상 종료, 앱 주소가 틀린 조기 실패, 멈춘 실행을 `kill`로 중단한 경우 모두 임시 디렉터리가 남지 않았다. 토큰이 삭제되면
`travel-app` target은 `down`이 되고 사유가 `unable to read authorization credentials … token`으로 표시된다. 이는 정상이며 패널
"scrape 대상 up"으로 볼 수 있다.

Redis를 비우거나 인증 캐시를 지우면 토큰이 무효가 될 수 있으므로 로그인 뒤에는 측정이 끝날 때까지 인증 상태를 유지한다.

## 3. k6 remote write

| 항목 | 값 |
|---|---|
| 출력 | `-o experimental-prometheus-rw` (k6 v2.3.0에서 동작 확인) |
| 서버 | `K6_PROMETHEUS_RW_SERVER_URL=http://host.docker.internal:9090/api/v1/write` |
| 통계 | `K6_PROMETHEUS_RW_TREND_STATS=p(50),p(95),p(99),avg,max` |
| 식별자 | `--tag testid=<값>`. 실행별로 분리되는 것을 확인했다 |
| 이미지 | `grafana/k6@sha256:e66db15b860113878fa74670e31f5e274830b7b6e42c8bff28b2f2d86a257603` |
| Prometheus | `--web.enable-remote-write-receiver` |

- `summaryTrendStats`는 종료 요약에만 적용된다. remote write는 `K6_PROMETHEUS_RW_TREND_STATS`가 정한 통계만 보낸다. 기본값은
  `p(99)`뿐이다
- **k6는 시간 지표를 초 단위로 보낸다.** `k6_plan_duration_p95`가 `0.0872`였고 k6 요약의 p95는 87.16 ms였다. 패널 단위는 `s`다
- 위 다이제스트는 로컬에 받아 둔 `grafana/k6:latest`의 실제 다이제스트와 일치한다.
  `docs/perf/baseline-2026-09-28.md`가 적은 `sha256:e66db15bd39661c3…`는 존재하지 않는 값이다. 앞 8자리만 같고 나머지가
  다르므로 전사 오류로 보이며, 그 문서는 이번 범위가 아니라 고치지 않았다

## 4. loadtest 프로파일의 SLO 버킷

`management.metrics.distribution.slo`를 `application-common-loadtest.yml`에만 둔다.

| 대상 | 경계 |
|---|---|
| `planb.travel.ai.orchestration` | 50ms, 100ms, 250ms, 500ms, 1s, 2s, 5s, 10s, 20s, 30s, 60s, 120s, 300s |
| `http.server.requests` | 10ms, 50ms, 100ms, 250ms, 500ms, 1s, 2s, 5s, 10s, 20s, 30s, 60s, 120s, 300s |

- 기본 Timer는 `_count`·`_sum`·`_max`만 내보내 `histogram_quantile`을 쓸 수 없다. percentile histogram 기본 범위는 30초에서
  끊긴다(69개 버킷, 마지막 유한 상한 30초)
- `http.client.requests`는 의존성 구분이 생기기 전까지, `planb.ai.retry`는 횟수와 결과만 보므로 버킷을 두지 않는다
- **경계는 잠정값이다.** 4A-2에서 OpenAI 고정 지연이 정해지면 실행 전에 확정한다
- 이 설정은 `loadtest` 프로파일에만 적용된다. 운영에는 이 시계열이 생기지 않는다

`LoadTestMetricsProfileTest`가 이 yml을 Spring Boot의 `PropertiesMeterFilter`에 그대로 넣어 버킷이 실제로 생기는지 확인한다.
smoke에서는 Prometheus의 `le` 라벨로 0.05초부터 이어지는 경계가 나오는 것을 확인했다(조회한 앞 12개 기준 120초까지). 300초까지의 전체
경계는 이 단위 테스트가 확인한다.

## 5. 대시보드

Grafana `http://localhost:3000/d/travel-load` (익명 Viewer, 로컬 전용).

| # | 패널 | 주요 쿼리 |
|---|---|---|
| 1 | k6 요청률 | `k6_iterations_total`, `k6_http_reqs_total`의 `rate` |
| 2 | k6 지연 p50/p95/p99 | `k6_plan_duration_p50/p95/p99/max` |
| 3 | k6 오류율·check 성공률 | `1 - k6_plan_success_rate`, `k6_checks_rate` |
| 4 | k6 VU·dropped iterations | `k6_vus`, `k6_dropped_iterations_total`의 `increase` |
| 5 | 서버 HTTP 요청률·오류율·in-flight | `http_server_requests_seconds_count`, `http_server_requests_active_seconds_count` |
| 6 | 서버 HTTP 지연 (SLO 버킷) | `histogram_quantile` |
| 7 | Travel orchestration 지연 (SLO 버킷) | `planb_travel_ai_orchestration_seconds_bucket` |
| 8 | Correction retry 횟수·결과 | `planb_ai_retry_seconds_count` |
| 9 | 외부 HTTP 호스트별 지연·상태 — 동일 호스트 의존성 병합 주의 | `http_client_requests_seconds_*` |
| 10 | Hikari active·idle·pending | `hikaricp_connections_*` |
| 11 | Hikari 점유 시간 | `hikaricp_connections_usage_seconds_*` |
| 12 | JVM 스레드·메모리 | `jvm_threads_live_threads`, `jvm_memory_used_bytes` |
| 13 | CPU | `process_cpu_usage`, `system_cpu_usage` |
| 14 | scrape 대상 up | `up` |

### 5-1. 해석 주의

- **패널 9는 P3 근거로 쓰지 않는다.** `ApiClient`가 URI 템플릿을 넘기지 않아 `uri` 태그가 `none`이고 구분 기준이 호스트뿐이다.
  실행해 확인한 태그: Kor2와 식품영양성분은 같은 호스트를 쓰면 합쳐지고, loadtest에서는 네 스텁이 모두 `localhost`다
- 패널 12의 스레드는 JVM 전체 스레드이지 Tomcat 요청 스레드 풀이 아니다
- 드롭이 0건이면 k6가 `dropped_iterations`를 내보내지 않고, 재시도가 없으면 `planb.ai.retry` meter가 만들어지지 않는다.
  패널 4는 `or vector(0)`으로 0을 표시하고, 패널 8은 비어 있는 것이 정상이다

## 6. 사용법

```bash
export PATH="/Applications/Docker.app/Contents/Resources/bin:$PATH"   # /usr/local/bin/docker 링크가 깨진 환경
```

1. Docker Desktop을 켠다
2. `docs/perf/baseline-2026-09-28.md` 5절대로 MySQL, Redis, 스텁(`./gradlew travelLoadTestStubs`)과 앱(`loadtest` 프로파일)을 띄운다
3. 실행한다

```bash
SCENARIO=plan-throughput PLAN_RATE=5 PLAN_DURATION=30s PRE_ALLOCATED_VUS=5 MAX_VUS=10 \
TESTID=my-run-1 src/test/observability/run-observed-load.sh
```

4. 끝나면 스크립트가 출력한 Grafana 링크(시간 범위와 `testid` 포함)를 연다
5. 중지: `SCRAPE_TOKEN_DIR=/tmp docker compose -f src/test/observability/docker-compose.yml down` (지표까지 지우려면 `-v`)

compose 파일이 `SCRAPE_TOKEN_DIR`를 필수로 요구하므로 `down`에도 값을 준다.

## 7. smoke 결과

| 항목 | 결과 |
|---|---|
| 조건 | 로컬 단일 프로세스, `loadtest` 프로파일, 스텁, 5 req/s, 30초, VU 5 (Phase 3과 같은 조건) |
| k6 | 150/150 성공, 실패 check 0, `dropped_iterations` 0, 종료 코드 0 |
| 지연 | avg 74.38 ms, p95 87.16 ms, p99 408.53 ms (Phase 3 기준선 p95 68~72 ms와 같은 규모) |
| Prometheus | `travel-app` target `up`, 앱·k6 시계열이 같은 `testid` 시간 범위에 수집됨 |
| 샘플 해상도 | 앱 지표는 37초 구간에서 16~19개 (2초 간격) |
| 패널 쿼리 | Grafana datasource proxy로 34개를 실행해 33개가 데이터를 반환. 나머지는 패널 8(재시도 없음) |
| 재실행 | 스택이 떠 있는 상태에서 다시 실행하면 Prometheus만 재생성되고 이전 `testid` 데이터가 유지됨 |
| 임시 JWT | 정상, 조기 실패, 중단 세 경로 모두 삭제됨 |

첫 실행에서 이미지를 처음 받은 직후 `docker compose up -d`가 Prometheus 컨테이너 시작 단계에서 9분 넘게 멈췄다. 중단하고 같은 옵션을
`docker run`으로 재현했을 때는 정상이었고, 두 번째 실행부터는 문제가 없었다. **원인은 규명하지 못했다.** 다시 멈추면 중단한 뒤
`down`하고 재시도한다.

## 8. 한계와 4A-2로 넘기는 것

- **Tomcat 요청 스레드 지표가 없다.** smoke의 지표 이름에는 `tomcat_sessions_*`만 있고 `tomcat_threads_*`가 없다. `server.tomcat.
  mbeanregistry.enabled` 기본값이 `false`이기 때문이다. 4A-2에서 `application-common-loadtest.yml`에만 켠다
- OpenAI 고정 지연 스텁이 없어서 이 실행은 P1·DB pool을 판정하지 못한다. 5 req/s에서 k6 최대 VU는 4였다
- SLO 경계는 잠정값이다
- 관리 포트는 `*:8081`, 즉 모든 인터페이스에 바인딩된다(실행해 확인). 로컬에서는 문제가 없지만 Railway 상시 수집은
  `SecurityConfig` 변경과 비노출 검증이 필요한 별도 Issue다
- Redis(Lettuce) 지표, 의존성별 외부 HTTP 지연, P3·P4, SDK 재시도 증폭은 필요한 계측이 없어 `증거 부족`을 유지한다
- 스텁 서버는 지표를 내보내지 않는다. 스텁이 병목인지는 이 스택으로 구분할 수 없다
- 패널 8의 쿼리는 재시도가 없는 실행에서만 검증했다. 재시도가 발생하는 실행에서 값을 확인하지 못했다
- Grafana 화면은 사람이 눈으로 확인하지 못했고, 같은 쿼리를 Grafana의 datasource proxy로 실행해 확인했다
