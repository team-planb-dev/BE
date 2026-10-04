# Phase 5B 가상 스레드 on/off 비교 결과

측정일: 2026-10-04  
Issue: [#131](https://github.com/team-planb-dev/BE/issues/131)  
실험 계획: `docs/perf/phase5b-virtual-thread-experiment.md` (commit `c33e233`로 실행 전 고정)  
선행 결과: `docs/perf/phase5b-concurrency-result.md` (#130)

## 1. 결론

> 로컬 스텁 조건에서 platform thread(P)는 36 req/s부터 Tomcat thread 200개가 모두 점유되어
> 일정 생성 p95가 10.06초(36 req/s), 15.98초(42 req/s)로 늘었음. 같은 조건에서
> `spring.threads.virtual.enabled`만 켠 가상 스레드(V)는 동시 요청이 최대 약 255개(actuator 포함)까지 늘어도
> p95 6.06초를 유지했고 실패·drop이 없었음. 사전 채택 기준 6개를 모두 충족해 V를
> **채택 후보**로 판정함.

채택 후보는 운영 적용 결정이 아님. 실제 OpenAI·Railway 조건은 측정하지 않았음. 운영 설정 변경은
별도 이슈에서 다룸(§6).

| 가설 | 판정 | 근거 |
|---|---|---|
| P에서 thread 고갈로 지연이 늘어남 | 확인 | 36·42 req/s에서 Tomcat busy 200/200, p95가 S1(6.07초)보다 4.0초·9.9초 증가 |
| V는 thread 고갈 구간에서 지연이 늘지 않음 | 확인 | 36·42 req/s p95 6.06초, 동시 요청 최대 218·255(actuator 포함) |
| V 채택 후보 (§7.2 기준 6개) | 확인 | §3.4 |
| 연결 실패(`dial: i/o timeout`)는 호스트 listen queue 초과 때문 | 기각 | 실패가 난 실행을 포함해 전 실행에서 `listen queue overflow` 증가 0 |
| 연결 실패는 Docker Desktop 연결 경로 때문 | 증거 부족 | Docker k6·호스트 k6 각 3회 반복에서 실패가 재현되지 않음. 계획 §7.1은 overflow 증가가 없으면 연결 경로 쪽으로 기록하라고 정했으나, 2단계에서 재현되지 않아 더 보수적으로 판정함(§4) |

### 사전 예측과 결과

| 실행 전 예측 (계획 §8) | 결과 |
|---|---|
| P S1·S2는 p95 약 6.1초, 실패 0 | p95 6.07·6.05초. S1에 연결 실패 1건(§4) |
| P S3(36 req/s) p95 약 10초 | 10.06초 |
| P S4(42 req/s) p95 20초 안팎, 실패보다 지연 증가가 먼저 | 15.98초, HTTP 실패 0. k6가 186건을 보내지 못해 실제 투입량이 예측 조건보다 적었음. drop을 반영해 계산하면 약 16.0초, drop이 없었다면 약 21초로 예측과 맞음(§3.2) |
| V는 S3·S4에서도 p95 약 6.1초 | 6.06초 |
| V 평균 점유 Hikari connection은 42 req/s에서 1개 안팎 | 0.90개 |
| V의 JVM live thread가 P보다 적음 | V 최대 62개, P 최대 258개 |
| pinned 이벤트는 적지만 0건이라고 예측하지 않음 | 8개 실행 모두 0건(임계값 20ms) |

## 2. 실행 환경과 조건

| 항목 | 값 |
|---|---|
| 기준 commit | `origin/dev` `dbf11b0ee37f252912176f5ee8e121581e2271c5` |
| 장비 | Apple M1 Pro 8 core, 16 GiB, macOS 15.7.7, Docker 27.4.0 (VM 8 CPU, 7.65 GiB) |
| JDK | 21.0.2, `./gradlew bootRun` (`loadtest` 프로필, DevTools 포함) |
| DB·Cache | MySQL 8.4, Redis 7.2 container. 모드마다 새로 시작 |
| Hikari / Tomcat | 최대 connection 10 / 최대 thread 200 (설정 override 없음) |
| 스텁 | OpenAI 호출당 3,000ms, 외부 API 지연 없음. 모드마다 새로 시작 |
| 하네스 | 다중 계정 `USER_COUNT=400`, k6 `constant-arrival-rate`, 단계별 60초, `GRACEFUL_STOP=60s` |
| 모드 | P: 환경변수 없음. V: `SPRING_THREADS_VIRTUAL_ENABLED=true`, `SPRING_MAIN_KEEP_ALIVE=true` |
| 실행 순서 | P 20 → 30 → 36 → 42 (11:34~11:47), 앱·DB·스텁 재시작, V 20 → 30 → 36 → 42 (11:48~12:01) |
| JFR | 단계마다 `jcmd <pid> JFR.start settings=default`. P·V 모두 같은 방식 |

V 모드에서는 platform `http-nio-8080-exec` thread가 0개였고 Tomcat thread pool 지표가 `-1`로 노출됨.
Spring Boot 문서대로 가상 스레드에서는 thread pool 설정과 지표가 의미를 잃으므로, V는 HTTP in-flight
요청 수로 동시성을 봄.

## 3. 결과

### 3.1 처리 결과

| 모드·요청률 | testid | 완료 | drop | 성공률 | p50 / p95 / p99 / 최대 | active VU 최대 |
|---|---|---:|---:|---:|---:|---:|
| P 20 | `phase5b-vt-p-20rps-20261004-113455` | 1200 | 0 | 1199/1200 | 6.04 / 6.07 / 6.39 / 6.96초 | 125 |
| P 30 | `phase5b-vt-p-30rps-20261004-113811` | 1801 | 0 | 100% | 6.04 / 6.05 / 6.06 / 6.08초 | 183 |
| P 36 | `phase5b-vt-p-36rps-20261004-114121` | 2121 | 39 | 100% | 8.51 / 10.06 / 10.35 / 10.53초 | 330 |
| P 42 | `phase5b-vt-p-42rps-20261004-114440` | 2335 | 186 | 100% | 11.74 / 15.98 / 16.51 / 16.68초 | 535 |
| V 20 | `phase5b-vt-v-20rps-20261004-114902` | 1200 | 0 | 100% | 6.05 / 6.08 / 6.39 / 6.99초 | 123 |
| V 30 | `phase5b-vt-v-30rps-20261004-115215` | 1800 | 0 | 100% | 6.04 / 6.06 / 6.08 / 6.18초 | 182 |
| V 36 | `phase5b-vt-v-36rps-20261004-115524` | 2160 | 0 | 100% | 6.04 / 6.06 / 6.07 / 6.10초 | 219 |
| V 42 | `phase5b-vt-v-42rps-20261004-115833` | 2521 | 0 | 100% | 6.04 / 6.06 / 6.13 / 6.31초 | 263 |

- 지연은 k6 `plan_duration`(`response.timings.duration`)임. 첫 바이트까지의 대기(`waiting`)는 포함하고,
  연결 준비(`blocked`)와 TCP 연결(`connecting`)은 포함하지 않음. 따라서 TCP accept 대기열에서 기다린
  시간이 있었다면 표에서 빠지며, 이는 P에 유리한 방향의 오차임
- P 20의 실패 1건은 HTTP 응답이 없는 연결 실패이며 `plan_duration` 0초로 기록돼 P 20의 지연 통계에 포함됨(§4)
- OpenAI 호출 최대 시간은 단계별 3.03~3.17초였음

### 3.2 P의 drop은 부하 발생기 쪽 현상임

P 36·42에서 k6가 예정된 요청 39건·186건을 시작하지 못했음(`dropped_iterations`). k6 `vus_max`는 P 36에서
300 → 339, P 42에서 350 → 536으로 실행 중에 늘었음. 응답이 늦어지자 사전 할당 VU가 모자랐고, k6가 실행 중에
VU를 추가하는 동안 시작하지 못한 요청이 drop됨. V는 `vus_max`가 사전 할당값(300·350)에서 늘지 않았고 drop도
없었음. 서버가 요청을 거부한 기록은 없음.

drop은 P의 실제 투입량을 줄였음. Little's law로 P의 처리 상한은 약 200 ÷ 6.04초 ≈ 33.1 req/s임. 처리하지 못한
초과분이 60초 동안 쌓인다고 보면 마지막 요청의 추가 대기는 다음과 같음.

| 단계 | 실제 투입(drop 반영) | 마지막 요청의 추가 대기 | 예상 p95 (6.04초 + 0.95 × 추가 대기) | 측정 p95 | drop이 없었을 때 예상 p95 |
|---|---:|---:|---:|---:|---:|
| P 36 | 36 − 0.65 = 35.35 req/s | (35.35 − 33.1) × 60 ÷ 33.1 ≈ 4.1초 | 약 9.9초 | 10.06초 | 약 11.0초 |
| P 42 | 42 − 3.1 = 38.9 req/s | (38.9 − 33.1) × 60 ÷ 33.1 ≈ 10.5초 | 약 16.0초 | 15.98초 | 약 21초 |

측정값이 이 단순 모델과 맞으므로, P의 지연 증가는 thread 고갈에 따른 대기로 설명됨. drop이 없었다면
P의 p95는 더 길었을 것이므로 실제 P와 V의 차이는 표와 같거나 더 클 가능성이 큼.

보조 근거로, 서버 측 요청 시간 histogram은 P 36·42를 포함한 8개 실행 모두 6~8초 버킷 안에 있었음(SLO 버킷
p95 7.9초는 버킷 내부 보간값이라 모든 실행에서 같음). 즉 P에서 늘어난 4~10초는 서버 요청 timer 바깥, thread를
배정받기 전 대기에서 생겼음. Tomcat 기본 max-connections(8192) 안에서는 연결이 이미 수락돼 있으므로 이 대기는
k6의 `waiting`에 포함됨. P의 동시 요청 지표가 200에서 멈춘 것도 대기 중인 요청이 집계되지 않기 때문임.

### 3.3 서버 자원

| 모드·요청률 | 동시 요청 최대 | Tomcat busy 최대 | Hikari active / pending / timeout | 획득 대기 최대 | 평균 점유 connection | process CPU 최대 | system CPU 최대 | heap 최대 | JVM thread 최대 |
|---|---:|---:|---|---:|---:|---:|---:|---:|---:|
| P 20 | 133 | 133 | 2 / 0 / 0 | 0.106초 | 0.50 | 13.1% | 59.5% | 7.4% | 197 |
| P 30 | 182 | 182 | 1 / 0 / 0 | 0.006초 | 0.63 | 9.9% | 50.3% | 7.9% | 239 |
| P 36 | 200 | 200 | 9 / 0 / 0 | 0.048초 | 1.00 | 10.7% | 54.5% | 9.3% | 258 |
| P 42 | 200 | 200 | 7 / 0 / 0 | 0.048초 | 1.01 | 11.5% | 54.7% | 9.5% | 258 |
| V 20 | 134 | - | 2 / 0 / 0 | 0.114초 | 0.52 | 12.9% | 49.7% | 8.2% | 62 |
| V 30 | 185 | - | 2 / 0 / 0 | 0.017초 | 0.66 | 10.2% | 48.9% | 9.5% | 60 |
| V 36 | 218 | - | 2 / 0 / 0 | 0.017초 | 0.76 | 10.5% | 53.1% | 10.5% | 60 |
| V 42 | 255 | - | 2 / 0 / 0 | 0.044초 | 0.90 | 12.1% | 60.1% | 11.8% | 62 |

- 동시 요청은 `http_server_requests_active_seconds_count` 합계이며 actuator 요청을 포함함
- process CPU는 구간 첫 2개 샘플을 뺀 최대값임(계획에 없던 처리이며 어떤 판정 기준에도 쓰지 않음). 계정
  400개를 만드는 setup 직후 샘플이 구간 시작에 걸리기 때문임. 전체 구간 최대값은 P 11.0~16.2%, V 11.1~15.7%로 두 모드가 비슷함
- 평균 점유 connection은 `hikaricp_connections_usage_seconds_sum` 증가량 ÷ `increase()` 범위의 정수 초
  (65·66·69·75초)임. 실제 구간은 65.2~75.8초임
- P 36·42의 Hikari active 최대 7~9는 대기하던 요청이 thread를 얻자마자 저장 트랜잭션을 함께 시작한
  순간으로 보임. pending과 timeout은 0이었음
- system CPU는 k6·DB·관측 도구를 포함한 장비 전체 값임
- OpenAI 호출 평균은 모든 단계에서 3.003~3.006초로 계획의 스텁 영향 기준(3.1초)을 넘지 않았음.
  WebClient 호출은 모두 `status=200`, `exception=none`

### 3.4 채택 기준 판정 (계획 §7.2)

| 기준 | 결과 | 판정 |
|---|---|---|
| 1. S3·S4에서 V p95가 P보다 20% 이상 낮음 | 36: 6.06 대 10.06초(-40%), 42: 6.06 대 15.98초(-62%) | 충족 |
| 2. 모든 단계에서 V 성공률 ≥ P | V 100% 전 단계, P는 S1 1199/1200 | 충족 |
| 3. V Hikari timeout 0, pending 연속 4초 이하 | timeout 0, pending 양수 샘플 0 | 충족 |
| 4. V 외부 API·OpenAI 오류 ≤ P | 두 모드 모두 0 | 충족 |
| 5. V heap 최대가 P보다 10%p 이상 높지 않음 | V 11.8%, P 9.5% (+2.3%p) | 충족 |
| 6. pinned 이벤트 0건 또는 요청 경로 아님 | 8개 실행 모두 0건(최대 지속시간은 이벤트가 없어 해당 없음) | 충족 |

## 4. 연결 실패 원인 확인

### 4.1 관측

- P 20에서 `dial: i/o timeout` 1건이 다시 발생함. #130 다중 계정 20 req/s와 같은 패턴임
- 실패한 연결 시도는 11:36:29 무렵 시작됨(실패 보고 11:36:59, iteration 최대 30초). 측정 구간 시작
  (11:36:28), 즉 setup이 끝나고 시나리오가 시작된 직후임
- 실행 전후 `netstat -s -p tcp`의 `listen queue overflow` 증가는 8개 실행 모두 0이었음. 실패가 난 실행도
  0이었음. 따라서 호스트 listen queue 초과는 원인이 아님

### 4.2 2단계: k6 실행 위치 비교

같은 조건(P 모드, 20 req/s, `USER_COUNT=400`)을 Docker k6와 호스트 k6로 번갈아 3회씩 실행함. 호스트 k6는
컨테이너와 같은 버전(v2.3.0, commit `e088784614`)의 공식 macOS 바이너리를 checksum 확인 후 임시 경로에서
사용했고 시스템에 설치하지 않았음. 이 실행은 원인 분리용이며 P/V 비교에는 넣지 않음.

| 실행 | k6 위치 | 완료 | 실패 | listen queue overflow 증가 |
|---|---|---:|---:|---:|
| r1 | Docker | 1201 | 0 | 0 |
| r1 | 호스트 | 1200 | 0 | 0 |
| r2 | Docker | 1201 | 0 | 0 |
| r2 | 호스트 | 1201 | 0 | 0 |
| r3 | Docker | 1201 | 0 | 0 |
| r3 | 호스트 | 1200 | 0 | 0 |

6회 모두 실패가 없어 Docker 경로와 호스트 경로의 차이를 확인하지 못함. 실패는 요청 수가 아니라 실행 단위로
보는 것이 맞음. 시나리오 시작 직후에만 발생했기 때문임. 이번 실험 8회 중 1회, #130에서 1회 관측됐음.
실행당 약 1/8로 가정하면 경로가 원인이라도 3회 연속 실패가 없을 확률은 약 67%(7/8의 세제곱)라 3회 반복으로는
판정할 수 없음.

계획 §7.1은 `listen queue overflow` 증가가 없으면 연결 경로(Docker Desktop) 쪽으로 기록하도록 정했음. 그러나
2단계 비교에서 경로 차이가 재현되지 않아, 이 문서는 더 보수적으로 `증거 부족`으로 판정함.

이 6회는 관측 도구 없이 k6만 실행했음. 실패한 실행과는 Prometheus·Grafana 재생성, remote write 유무까지
함께 달라서 k6 실행 위치만 분리한 비교가 아님. 관측 도구 영향은 가설로만 남기되, 시점이 맞지 않는 점도
기록함. container 재생성은 스크립트 시작 무렵(P 20은 약 11:34:55)이고 실패한 연결 시도는 계정 400개 setup이
끝난 뒤인 약 11:36:29로 약 90초 차이가 있음.

## 5. 해석

- P의 처리량 상한은 thread 수 ÷ 요청 1건 처리 시간 ≈ 200 ÷ 6.04초 ≈ 33 req/s임. 이를 넘는 요청은
  Tomcat 대기열에서 thread를 기다리며 그만큼 지연이 늘었음
- V는 요청마다 가상 스레드를 쓰므로 3초 × 2회의 OpenAI 대기 동안 platform thread를 붙잡지 않음. 이번
  조건에서 다음 제약(Hikari, CPU, 메모리)에는 42 req/s까지 도달하지 않았음
- V가 단일 요청을 빠르게 만든 것은 아님. thread가 고갈되지 않은 20·30 req/s에서는 두 모드의 p95가
  같았음

## 6. 판정 후 행동

- 이 이슈에서는 운영 설정을 바꾸지 않음
- 운영 적용을 검토하려면 별도 이슈에서 다음을 확인해야 함
  - 운영 프로필에 `spring.threads.virtual.enabled`, `spring.main.keep-alive`를 켤 때의 영향 범위
    (`@Async`·스케줄러·STOMP·WebClient 등 Spring이 가상 스레드를 쓰게 되는 다른 실행기)
  - 실제 OpenAI 지연·rate limit 조건과 Railway 인스턴스 자원에서의 효과
  - 동시 요청 상한이 사라지므로 외부 API rate limit, Hikari, 메모리를 보호할 다른 상한이 필요한지
- 연결 실패 원인은 `증거 부족`으로 남김. 반복 측정이 필요해지면 관측 도구를 미리 띄운 상태와 실행 중
  재생성하는 상태를 비교함

## 7. 측정 한계

- 모든 외부 의존성은 로컬 정상 응답 스텁임. 실제 OpenAI·외부 API·Railway 처리량으로 일반화하지 않음
- 단일 앱 인스턴스, 생성 경로 단독 부하, 계정 400개. 편집 preview는 포함하지 않음
- P와 V는 서로 다른 JVM·DB 인스턴스에서 실행함. 각 모드 안에서는 단계를 오름차순으로 연속 실행함
- 모드·요청률 조합마다 1회만 실행했고 반복하지 않았음. 36·42 req/s의 P·V 차이(p95 4~10초)는 단계 간
  같은 요청률의 모드 간 차이(30 req/s 이하에서 0.03초 안팎)보다 훨씬 커서 판정에는 영향이 없다고 봄
- P 36·42는 k6 drop으로 실제 투입량이 예정보다 적었음(§3.2)
- JFR pinned 이벤트는 기본 임계값 20ms 이상만 기록됨. 그보다 짧은 pinning은 측정하지 않음
- 앱·부하 발생기·DB·관측 도구가 한 장비를 공유함
- 측정 구간 정의는 #130 계획 §11.2와 같음. 카운터 기반 값은 Prometheus `increase()` 외삽값임
- Prometheus scrape 간격은 2초임

## 8. 증거

- Prometheus 쿼리 결과: `docs/perf/phase5b-vt-results/<testid>.json`
- 실행별 JFR pinned 이벤트 수·연결 실패 수·`listen queue overflow` 증가: `docs/perf/phase5b-vt-results/<testid>.run.txt`
- k6 실행 위치 비교: `docs/perf/phase5b-vt-results/phase5b-path-summary.txt`
- Grafana 고정 시간 범위 링크: 각 결과 JSON의 `k6.grafana` 필드(로컬 Prometheus volume, retention 2일)
- k6 원본 로그, Grafana 캡처, JFR 원본(`.jfr`)은 Git에 넣지 않고 작성자 로컬 보관 디렉터리
  (`Yeoro 프로젝트 모음/리팩토링 문서/텍스트 문서/phase5b-vt-evidence/`)에 보존함
- 토큰 파일은 실행 스크립트가 매 실행 후 삭제함. 로그와 결과 JSON에 JWT·Bearer 문자열이 없음을 확인함

## 9. 재현

#130 결과 문서 §9의 MySQL·Redis·스텁 실행 방법과 같음. 모드에 따라 앱 실행 환경변수만 다름.

```bash
# V 모드. P 모드는 앞의 두 변수를 빼고 실행함
SPRING_THREADS_VIRTUAL_ENABLED=true SPRING_MAIN_KEEP_ALIVE=true \
SPRING_PROFILES_ACTIVE=loadtest \
DATABASE_URL=jdbc:mysql://localhost:33306/planb_loadtest \
DATABASE_USERNAME=planb DATABASE_PASSWORD=planb \
REDIS_URL=redis://localhost:36379 \
JWT_SECRET=01234567890123456789012345678901 \
./gradlew bootRun

# 단계마다 JFR을 건 뒤 실행함
jcmd <app-pid> JFR.start name=<testid> settings=default filename=<testid>.jfr

USER_COUNT=400 TESTID="phase5b-vt-v-42rps-$(date +%Y%m%d-%H%M%S)" \
SCENARIO=plan-throughput PLAN_RATE=42 PLAN_DURATION=60s \
PRE_ALLOCATED_VUS=350 MAX_VUS=1200 GRACEFUL_STOP=60s \
src/test/observability/run-observed-load.sh

jcmd <app-pid> JFR.stop name=<testid>
jfr summary <testid>.jfr | grep VirtualThreadPinned
```
