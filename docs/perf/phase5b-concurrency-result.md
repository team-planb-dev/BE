# Phase 5B 동시 요청·다중 계정 포화 자원 결과

측정일: 2026-10-04  
Issue: [#130](https://github.com/team-planb-dev/BE/issues/130)  
실험 계획: `docs/perf/phase5b-concurrency-experiment.md`
(공유 계정 실험은 commit `1895b91`, 다중 계정 부하 단계는 commit `39323cd`로 실행 전 고정. 다중 계정
smoke와 setup 시간 확인은 이 commit 전에 실행한 준비 단계임)

이 문서는 서로 다른 두 하네스의 결과를 담음. 두 결과는 합치지 않고 따로 판정함.

| 하네스 | 요청 주체 | 의미 | testid |
|---|---|---|---|
| A. 공유 계정 | `setup()`이 만든 계정 1개·동행인 1명을 모든 VU가 공유 | 요청 간 동시성. Phase 4A·5A와 같은 스크립트 조건 | `phase5b-<rate>rps-*` |
| B. 다중 계정 | 계정 200개, 계정마다 자기 소유 동행인 1명. 요청마다 `iterationInTest % 200`으로 계정 선택 | 서로 다른 사용자의 동시 요청 | `phase5b-multiuser-<rate>rps-*` |

## 1. 결론

> **A. 공유 계정**: 1~30 req/s 6단계 모두 성공률 100%, drop·interrupt 0, p95 6.06~6.14초.
> 사전 포화 기준에 처음 도달한 자원은 Tomcat 요청 thread임. 30 req/s에서 busy 최대
> 182/200(0.91), busy/max 0.8 이상이 2초 간격 연속 28개 샘플(약 54~56초) 이어짐.
> 같은 구간에서 Hikari pending 0, 앱 process CPU 최대 8.6%, heap 최대 7.8%였음.
>
> **B. 다중 계정**: 1~20 req/s에서 서버 자원은 공유 계정과 같은 수준이었음. Hikari pending 0,
> Tomcat busy 최대 122/200(0.61), 앱 CPU 최대 10.6%(setup 샘플 제외, B 20). 다만 20 req/s에서 1,201건 중
> 1건이 k6의 `dial: i/o timeout`(TCP 연결 실패)으로 실패해 사전 진행 조건(성공률 100%)을 충족하지 못함.
> 계획에 따라 30 req/s는 실행하지 않음.

Tomcat은 공유 계정 30 req/s에서 사전 기준(busy/max ≥ 0.8, 10초 이상)으로 포화 판정됨. 다만
200개 thread가 모두 점유되지는 않아 대기열·지연 증가·서버 오류는 관측되지 않음. 처리량 한계
자체는 두 하네스 모두 측정 범위 밖임.

| 가설 | A. 공유 계정 | B. 다중 계정 (1~20 req/s) |
|---|---|---|
| 5A 이후 Hikari가 다시 먼저 포화됨 | 기각. pending 양수 샘플 0, timeout 0, 획득 대기 최대 0.006초, active 최대 2/10 | 기각. pending 양수 샘플 0, timeout 0, 획득 대기 최대 0.013초, active 최대 2/10 |
| Tomcat 요청 thread가 먼저 포화됨 | 확인(사전 기준). 30 req/s busy 최대 182/200, ≥ 0.8 연속 28개 샘플 | 증거 부족. 20 req/s busy 최대 122/200(0.61), 30 req/s 미실행 |
| Tomcat thread 고갈로 처리량이 제한됨 | 증거 부족. busy가 200에 도달하지 않았고 지연·실패 증가 없음 | 증거 부족 |
| 앱 CPU가 먼저 포화됨 | 기각. process CPU 최대 8.6% | 기각. process CPU 최대 10.6%(B 20, setup 샘플 제외). 구간 10초 이후 기준으로는 7.2% |
| JVM 메모리가 먼저 포화됨 | 기각. heap 최대 7.8% | 기각. heap 최대 5.8% |
| 외부 HTTP client가 먼저 포화됨 | 스텁 조건에서 기각 | 스텁 조건에서 기각 |
| 사용자 간 데이터가 섞임 | 해당 없음(계정 1개) | smoke(3계정·6건)에서 미관측. 소유자 조회 200·`planName` 일치, 다른 계정 조회 403. 부하 중 격리는 검증하지 않음 |

외부 HTTP 판정: WebClient 호출은 모두 `status=200`, `outcome=SUCCESS`, `exception=none`. OpenAI
호출은 모두 `status=200`, `outcome=SUCCESS`임. 외부 API 스텁은 지연이 없고 OpenAI는 고정 지연
스텁이므로, 이 판정은 실제 공급자 조건에 적용하지 않음.

### 사전 예측과 결과

| 실행 전 예측 | 결과 |
|---|---|
| (A, 계획 §8) Tomcat busy/max는 약 25 req/s에서 0.8에 도달하고 30 req/s에서 처음 포화됨 | 20 req/s 0.605, 30 req/s 0.91. 30 req/s busy는 예측 약 195개, 측정 182개. Little's law로 0.8 도달은 약 26.4 req/s(200 × 0.8 ÷ 6.05초)이며 직접 측정하지 않음. 30 req/s 첫 포화는 예측과 일치 |
| (A) 요청 1건의 thread 점유는 약 6.2~6.5초 | k6 평균 6.05~6.12초(클라이언트 측 값, 서버 점유의 상한). 예측보다 약간 짧음 |
| (A) Hikari와 앱 CPU는 30 req/s까지 포화되지 않음 | pending 0, timeout 0, process CPU 최대 8.6%. 예측과 일치 |
| (B, 계획 §11.4) 자원 점유는 공유 계정과 같은 수준 | 1~20 req/s에서 Tomcat busy·in-flight·Hikari pending이 같은 수준. 일정당 connection 점유 시간은 약간 김(§3.6) |
| (B) 일정당 Hikari 점유가 조금 늘 수 있으나 pending은 0 | 일정당 점유가 약간 길고 pending 0. 다만 다른 JVM이라 계정 수의 효과로 귀속하지 않음(§3.6) |
| (B) 30 req/s에서 Tomcat이 처음 포화됨 | 20 req/s 진행 조건 실패로 30 req/s 미실행. 판정 불가 |

## 2. 실행 환경과 조건

| 항목 | 값 |
|---|---|
| 기준 commit | `origin/dev` `a23080ab4fa036c5987389a376b916b0fc4a0751` |
| 장비 | Apple M1 Pro 8 core, 16 GiB, macOS 15.7.7 |
| Docker | 27.4.0, VM 8 CPU, 7.65 GiB |
| JDK | 21.0.2, `./gradlew bootRun` (`loadtest` 프로필, DevTools 포함) |
| DB·Cache | MySQL 8.4.10, Redis 7.2.16 container |
| Hikari 최대 connection | 10 (`hikaricp_connections_max` 실측) |
| Tomcat 최대 thread | 200 (`tomcat_threads_config_max_threads{name="http-nio-8080"}` 실측) |
| OpenAI 스텁 | 정상 응답, 호출당 3,000ms 고정 지연 |
| 외부 API 스텁 | Kor2·Kakao Map·Kakao Mobility·영양 API, 지연 없음 |
| 부하 | k6 `constant-arrival-rate`, 단계별 60초, `GRACEFUL_STOP=30s`, VU는 계획 §3 표 |
| A 실행 | 00:56~01:09, 앱 JVM 1개에서 1 → 30 req/s 연속 실행 |
| B 실행 | 11:00 MySQL·Redis(새 DB)·스텁·앱 재기동 후 smoke·setup 확인을 거쳐 11:03~11:15, 1 → 20 req/s 연속 실행 |
| 관측 도구 | `run-observed-load.sh`가 단계마다 새 scrape token으로 Prometheus·Grafana container를 다시 만듦 |

A와 B는 서로 다른 앱 JVM에서 실행했음. 앱·스텁(호스트 JVM)과 k6·Prometheus·Grafana·MySQL·
Redis(Docker)가 같은 장비를 공유함.

## 3. 단계별 결과

### 3.1 사전 기준 판정

| 하네스·단계 | 진행 조건 | 중단 조건 | 포화 기준 도달 | VU 최대 / pre / max |
|---|---|---|---|---:|
| A 1 req/s | 통과 | 미발생 | 없음 | 7 / 10 / 20 |
| A 3 req/s | 통과 | 미발생 | 없음 | 19 / 24 / 60 |
| A 5 req/s | 통과 | 미발생 | 없음 | 31 / 40 / 100 |
| A 10 req/s | 통과 | 미발생 | 없음 | 61 / 80 / 200 |
| A 20 req/s | 통과 | 미발생 | 없음 | 122 / 160 / 400 |
| A 30 req/s | Tomcat 기준 미충족 | 미발생 | Tomcat | 183 / 240 / 600 |
| B 1 req/s | 통과 | 미발생 | 없음 | 6 / 10 / 20 |
| B 3 req/s | 통과 | 미발생 | 없음 | 19 / 24 / 60 |
| B 5 req/s | 통과 | 미발생 | 없음 | 31 / 40 / 100 |
| B 10 req/s | 통과 | 미발생 | 없음 | 62 / 80 / 200 |
| B 20 req/s | 성공률 1,200/1,201(99.92%, k6 표기 99.91%)로 미충족 | 미발생 | 없음 | 126 / 160 / 400 |

진행 조건은 성공률 100%, drop·interrupt 0, Hikari pending 양수 샘플 연속 2개(4초) 이하·timeout 0,
Tomcat busy/max 0.8 미만, CPU 0.8 미만, 외부 HTTP 오류 0, p95 9.21초(A 1 req/s p95 6.14초의
1.5배) 이하임. B의 p95 기준은 B 1 req/s p95 6.17초의 1.5배(9.26초)로 적용했으며 모든 단계가 통과함. 모든 단계에서 VU 최대값이 preAllocatedVUs보다 작아 VU 수는 제약이 아니었음.

### 3.2 처리 결과

| 하네스·단계 | testid | 완료 | 실패 | drop / interrupt | 평균 / p95 / p99 | in-flight 최대 |
|---|---|---:|---:|---:|---:|---:|
| A 1 | `phase5b-1rps-20261004-005647` | 61 | 0 | 0 / 0 | 6.12 / 6.14 / 6.35초 | 7 |
| A 3 | `phase5b-3rps-20261004-005958` | 180 | 0 | 0 / 0 | 6.11 / 6.14 / 6.15초 | 18 |
| A 5 | `phase5b-5rps-20261004-010152` | 300 | 0 | 0 / 0 | 6.09 / 6.12 / 6.15초 | 31 |
| A 10 | `phase5b-10rps-20261004-010339` | 601 | 0 | 0 / 0 | 6.06 / 6.09 / 6.10초 | 61 |
| A 20 | `phase5b-20rps-20261004-010526` | 1201 | 0 | 0 / 0 | 6.05 / 6.06 / 6.08초 | 121 |
| A 30 | `phase5b-30rps-20261004-010716` | 1801 | 0 | 0 / 0 | 6.05 / 6.06 / 6.07초 | 182 |
| B 1 | `phase5b-multiuser-1rps-20261004-110338` | 61 | 0 | 0 / 0 | 6.13 / 6.17 / 6.18초 | 7 |
| B 3 | `phase5b-multiuser-3rps-20261004-110608` | 180 | 0 | 0 / 0 | 6.11 / 6.16 / 6.18초 | 19 |
| B 5 | `phase5b-multiuser-5rps-20261004-110837` | 301 | 0 | 0 / 0 | 6.11 / 6.16 / 6.19초 | 31 |
| B 10 | `phase5b-multiuser-10rps-20261004-111107` | 600 | 0 | 0 / 0 | 6.08 / 6.12 / 6.14초 | 62 |
| B 20 | `phase5b-multiuser-20rps-20261004-111336` | 1201 | 1 | 0 / 0 | 6.05 / 6.08 / 6.11초 | 122 |

- 지연은 k6 종료 요약의 `plan_duration`(`response.timings.duration`)임. TCP 연결·대기(blocked) 시간은
  포함하지 않음. 그래서 B 20의 실패 1건은 iteration은 약 30초 걸렸지만 `plan_duration` 0초로 기록됨
- 서버 SLO 버킷 기반 p95는 6초와 8초 경계 사이 보간 때문에 7.9초로 표시됨. 판정에는 k6 값을 사용함
- k6 요약에 `dropped_iterations` 항목이 없으면 0으로 기록함. k6는 drop이 없으면 이 항목을 출력하지 않음
- k6 요약의 iteration/s(A 30 req/s에서 27.17)는 setup과 graceful stop 시간을 분모에 포함함
- in-flight는 `http_server_requests_active_seconds_count`의 전체 URI 합계라 actuator 요청을 포함함

### 3.3 B 20 req/s 실패 1건

- k6 로그: `Request Failed ... Post "http://host.docker.internal:8080/api/v1/travel/add-with-recommend": dial: i/o timeout`
  (보고 11:14:55). k6 요약의 `iteration_duration` 최대가 30초이므로 실패한 연결 시도는 약 11:14:25,
  즉 setup이 끝나고 시나리오가 시작된 직후에 시작됨
- 그 10초 동안 8080 connector 연결 수는 2 → 31 → 71 → 111 → 151 → 160으로, Tomcat busy는 1 → 121로
  늘었음. 실패는 이 연결 증가 구간에서 시작됨. 다만 A 30 req/s는 같은 10초 동안 1 → 242개로 더 크고 빠르게
  늘었는데도 실패가 0건이었으므로, 연결 증가만으로는 이 실패를 설명할 수 없음
- 같은 구간에 서버 자원(Tomcat thread·앱 CPU·Hikari) 포화 증거는 없음. busy 최대 122/200, Hikari pending 0,
  서버가 기록한 일정 생성 응답은 모두 200 SUCCESS. 연결 시도 구간(11:14:25~55)의 system CPU는 대부분
  40~50%였고, 11:14:37에 한 번 69.8%(전체 단계 최대)를 기록함
- `dial: i/o timeout`은 TCP 연결 요청(SYN)에 응답을 받지 못했다는 뜻임. 실패 지점이 Docker Desktop
  port forwarding인지, 호스트의 listen queue(Tomcat `acceptCount` 기본 100)인지는 분리하지 않았음.
  listen queue 유실은 Tomcat·Micrometer 지표로 보이지 않음. 다만 30초 동안 SYN 재시도가 모두 응답받지
  못했고, 그동안 Tomcat은 busy 121/200·연결 수 160으로 안정적이었음. 일시적인 listen queue 초과는 짧게
  해소되므로 Docker Desktop 전달 연결이 멈췄을 가능성이 더 높아 보임. 실패 직후 11:14:59에 연결 수가
  160 → 161로 늘어 실패한 VU가 다시 연결한 정황과도 맞음. 이 판단은 검증하지 않은 추정임
- 사전 진행 조건은 원인과 관계없이 성공률 100%였으므로 30 req/s로 진행하지 않음
- A 20·30 req/s에서는 같은 경로의 실패가 0건이었음. #131에서는 실행 전후 `netstat -s -p tcp`의 listen queue
  overflow 수를 비교하거나 k6를 Docker 밖에서 실행해 실패 지점을 분리해야 함

### 3.4 Hikari

| 하네스·단계 | active 최대 | pending 최대 / 양수 샘플 | timeout 증가 | 획득 대기 최대 | 평균 점유 connection | 일정당 점유 시간 |
|---|---:|---:|---:|---:|---:|---:|
| A 1 | 0 | 0 / 0 of 33 | 0 | 0.005초 | 0.051 | 0.054초 |
| A 3 | 0 | 0 / 0 of 33 | 0 | 0.006초 | 0.143 | 0.052초 |
| A 5 | 1 | 0 / 0 of 33 | 0 | 0.006초 | 0.215 | 0.047초 |
| A 10 | 1 | 0 / 0 of 33 | 0 | 0.004초 | 0.336 | 0.036초 |
| A 20 | 1 | 0 / 0 of 33 | 0 | 0.006초 | 0.449 | 0.024초 |
| A 30 | 2 | 0 / 0 of 33 | 0 | 0.006초 | 0.643 | 0.023초 |
| B 1 | 1 | 0 / 0 of 33 | 0 | 0.007초 | 0.052 | 0.055초 |
| B 3 | 1 | 0 / 0 of 33 | 0 | 0.012초 | 0.159 | 0.057초 |
| B 5 | 1 | 0 / 0 of 33 | 0 | 0.011초 | 0.257 | 0.056초 |
| B 10 | 2 | 0 / 0 of 33 | 0 | 0.013초 | 0.392 | 0.042초 |
| B 20 | 2 | 0 / 0 of 33 | 0 | 0.007초 | 0.538 | 0.029초 |

- active 최대는 2초 간격 gauge 샘플의 최대값임. 수십 ms 단위의 짧은 점유는 샘플에 잡히지 않을 수 있음
- 평균 점유 connection = 측정 구간의 `hikaricp_connections_usage_seconds_sum` 증가량 ÷ 65초(실제 구간
  65.2~65.8초, 영향은 무시할 수준)
- 일정당 점유 시간 = 같은 증가량 ÷ k6 완료 건수
- 두 값 모두 Prometheus `increase()` 외삽값임. 정확한 측정값이 아니라 크기 비교용임
- 일정당 connection 획득 횟수는 두 하네스 모든 단계에서 약 2.0회(1.98~2.03)로 일정함. 일정당 점유
  시간이 단계마다 줄어든 것은 connection 1회당 점유가 짧아진 결과임(A 0.027초 → 0.011초).
  단계를 같은 JVM에서 오름차순으로 실행했으므로 warm-up과 요청률의 효과를 분리할 수 없음

### 3.5 Tomcat·CPU·JVM

| 하네스·단계 | Tomcat busy 최대 | busy/max 최대 | ≥ 0.8 샘플 | process CPU 최대 | system CPU 최대 | heap 최대 | JVM thread 최대 |
|---|---:|---:|---:|---:|---:|---:|---:|
| A 1 | 7 | 0.035 | 0 | 5.2% | 51.6% | 3.6% | 63 |
| A 3 | 18 | 0.090 | 0 | 4.4% | 35.7% | 3.8% | 72 |
| A 5 | 31 | 0.155 | 0 | 4.2% | 38.4% | 3.8% | 84 |
| A 10 | 61 | 0.305 | 0 | 5.0% | 37.1% | 4.4% | 115 |
| A 20 | 121 | 0.605 | 0 | 6.4% | 45.9% | 6.3% | 175 |
| A 30 | 182 | 0.910 | 28 연속 | 8.6% | 56.6% | 7.8% | 237 |
| B 1 | 7 | 0.035 | 0 | 3.0% (11.1%) | 29.2% | 3.7% | 63 |
| B 3 | 19 | 0.095 | 0 | 3.6% (11.1%) | 34.7% | 3.7% | 72 |
| B 5 | 31 | 0.155 | 0 | 4.2% (11.0%) | 52.8% | 3.6% | 84 |
| B 10 | 62 | 0.310 | 0 | 6.6% (10.9%) | 41.1% | 4.5% | 115 |
| B 20 | 122 | 0.610 | 0 | 7.2% (11.1%) | 69.8% | 5.8% | 179 |

- B의 process CPU는 구간 시작 10초 이후의 최대값이고, 괄호는 전체 구간 최대값임. B는 setup에서
  계정 200개를 만들며, 괄호의 약 11%는 구간 시작 시점에 걸친 setup 샘플 값임. 10초 기준은 사전
  등록한 규칙이 아니며, 부하가 올라가는 구간의 샘플도 함께 빠짐. setup 직후 2샘플만 빼면 B 20의 최대는
  10.6%(11:14:33, busy가 121에 도달한 시점)임. A의 최대값은 이런 제외 없이 계산했으므로 A와 B의 CPU
  최대값은 같은 기준이 아님. 표의 값은 결과 JSON과 같은 2초 step query_range로 계산했으며, 샘플 정렬에
  따라 0.5%p 안팎 달라질 수 있음. 어느 기준으로도 사전 기준 0.8과는 거리가 멀어 판정은 바뀌지 않음
- heap 최대는 heap 사용량 합계 ÷ G1 Old Gen 최대(4 GiB)임. Eden·Survivor는 최대값이 `-1`로 노출됨
- system CPU는 장비 전체 사용률임. 부하가 낮은 구간에도 약 20~35%가 관측돼 대부분 앱 외 배경
  작업(k6, MySQL, Prometheus, Grafana, 스텁, 기타 프로세스)임. 이 장비 구성에서 system CPU는 앱 포화
  신호로 쓰기 어려움. 사전 기준의 0.8에는 어느 단계도 도달하지 않음

### 3.6 다중 계정과 공유 계정의 차이

같은 요청률에서 Tomcat busy, in-flight, Hikari pending, 외부 호출 수는 두 하네스가 같은 수준임.
일정당 Hikari 점유 시간은 B가 약간 김(10 req/s 0.042초 대 0.036초, 20 req/s 0.029초 대 0.024초).
두 하네스는 서로 다른 JVM에서 실행됐고 각 JVM의 warm-up 단계가 다르므로, 이 차이를 계정 수의
효과로 해석하지 않음. 어느 경우에도 평균 점유 connection은 10개 중 1개 미만임.

### 3.7 외부 호출

두 하네스 모든 단계에서 WebClient 호출은 `status=200`, `outcome=SUCCESS`, `exception=none`만 관측됨.
OpenAI `okhttp_requests`에는 `exception` 라벨이 없어 `status=200`, `outcome=SUCCESS`로 판정함.
일정당 호출 수는 OpenAI 약 2.0~2.07회, WebClient(외부 API 4개 합계) 약 6.9~7.2회임. `increase()`
외삽값이므로 개별 호출 계약의 증거로 쓰지 않음. 호출 계약은 `TravelLoadTestSmokeIntegrationTest`로
검증함. OpenAI 응답 최대는 약 3.07초로 스텁 지연과 일치함.

## 4. 해석

### 4.1 Tomcat 점유는 Little's law와 일치함

요청은 `constant-arrival-rate`로 일정한 간격으로 도착하고 처리 시간 편차가 작음. 따라서 동시 요청
수의 최대값이 평균(요청률 × 평균 처리 시간)과 거의 같게 나타남. 각 단계의 k6 평균 처리 시간을 사용함.

| 요청률 | A 평균 처리 시간 | A 예상 동시 요청 | A busy 최대 | B busy 최대 |
|---:|---:|---:|---:|---:|
| 1 | 6.12초 | 6.1 | 7 | 7 |
| 3 | 6.11초 | 18.3 | 18 | 19 |
| 5 | 6.09초 | 30.4 | 31 | 31 |
| 10 | 6.06초 | 60.6 | 61 | 62 |
| 20 | 6.05초 | 121.0 | 121 | 122 |
| 30 | 6.05초 | 181.5 | 182 | 미실행 |

같은 관계로 보면 200개 thread는 약 33 req/s에서 모두 점유됨. 그 이상에서는 요청이 connector
대기열에서 기다리며 지연이 늘어날 것으로 예상됨. 이 값은 측정하지 않은 예측임. 스텁 서버 자체의
동시 처리 능력과 k6 → Docker Desktop → 호스트 연결 경로의 한계도 측정하지 않았음. 따라서 33 req/s
초과에서는 이들이 Tomcat보다 먼저 제약이 될 수 있음.

### 4.2 Phase 4A-2와의 비교는 맥락 정보임

Phase 4A-2(`docs/perf/phase4a2-resource-saturation-result.md`)는 트랜잭션 분리 이전 코드에서 같은
3초 OpenAI 지연, 같은 공유 계정 스크립트로 3 req/s를 측정했음. 결과는 Hikari active 10/10, pending
최대 47(약 84초 지속), p95 31.486초였음. 이번 A에서는 30 req/s에서도 Hikari active 최대 2/10, pending
0이었음. active는 두 실험 모두 2초 간격 gauge 샘플임. 코드와 측정 시점이 다르므로 이 차이를 하나의
변경 효과로 계량하지 않음.

## 5. Phase 5A 결과와 비교

같은 공유 계정 스크립트·부하 조건이고, 코드는 다름(5A 측정은 최종 Facade 책임 정리 이전, 5B는 병합
이후). 따라서 이 표는 회귀가 없음을 확인하는 용도이며, 원인 비교가 아님.

| 지표 | 5A 1 req/s | 5B-A 1 req/s | 5A 3 req/s | 5B-A 3 req/s |
|---|---:|---:|---:|---:|
| 완료 | 61 | 61 | 181 | 180 |
| drop / interrupt | 0 / 0 | 0 / 0 | 0 / 0 | 0 / 0 |
| 성공률 | 100% | 100% | 100% | 100% |
| p95 | 6.13초 | 6.14초 | 6.13초 | 6.14초 |
| Hikari pending 최대 | 0 | 0 | 0 | 0 |
| connection 점유 최대 | 0.499초 | 0.416초 | 0.499초 | 0.134초 |
| 획득 대기 최대 | 0.006초 | 0.005초 | 0.010초 | 0.006초 |
| timeout 증가 | 0 | 0 | 0 | 0 |

최종 병합 코드에서도 5A 측정과 같은 수준의 결과가 나옴. 회귀는 관측되지 않음.

## 6. 판정 후 행동

- 사전 계획 §9에 따라 Hikari pool 확대와 DB 관련 변경은 하지 않음
- 외부 HTTP 대기·오류 증거가 없으므로 pool 계측을 추가하지 않음
- A에서 Tomcat이 먼저 포화 기준에 도달했고 앱 CPU·DB·외부 HTTP에 여유가 있음. 따라서 가상 스레드
  on/off 단일 변수 실험의 **적용 조건은 충족**함
- 다만 30 req/s까지 지연·서버 오류 증가가 없어 운영 코드 변경이 **필요하다는 증거는 없음**.
  이번 이슈에서는 운영 코드와 설정을 변경하지 않음
- 가상 스레드 실험은 후속 Issue [#131](https://github.com/team-planb-dev/BE/issues/131)로 분리함.
  thread 고갈 이후 구간(약 33 req/s 초과)을 다중 계정 하네스로 측정해야 하며, 그 전에 스텁 동시 처리
  능력과 k6 → 호스트 연결 경로(§3.3의 `dial: i/o timeout`)가 제약이 아님을 먼저 확인해야 함
- 요청 내부 병렬화(5C)는 이번 이슈에 포함하지 않음

## 7. 측정 한계

- 모든 외부 의존성은 로컬 정상 응답 스텁임. 실제 OpenAI의 지연·rate limit·오류와 Railway 자원
  제한은 반영하지 않음. 이 결과를 실제 운영 처리량으로 일반화하지 않음
- 단일 앱 인스턴스, 생성 경로 단독 부하임. 편집 preview는 포함하지 않음
- 다중 계정은 200개이며 계정마다 동행인 1명, 같은 여행 조건을 사용함. 사용자별 데이터 크기 차이는
  반영하지 않음
- 앱·부하 발생기·DB·관측 도구가 한 장비를 공유함. k6는 Docker Desktop을 거쳐 호스트 앱에 접속함
- A와 B는 서로 다른 앱 JVM에서 실행했음. 각 하네스 안에서는 단계를 같은 JVM에서 오름차순으로 연속
  실행했고 별도 warm-up 실행은 없었음. 단계 간 감소 추세(일정당 connection 점유 시간, p95)는 요청률의
  효과로 해석할 수 없음. 포화 자원 판정은 이 추세에 의존하지 않음
- 측정 구간은 해당 `testid`의 `setup` 요청 마지막 k6 샘플 시각부터 마지막 `k6_vus` 샘플 시각까지(약
  65초)임. k6가 약 5초마다 전송하므로 시나리오 시작 후 최대 5초가 빠질 수 있으나 계정 준비는 포함하지
  않음. remote write 시계열은 staleness marker가 없어 실제 샘플 시각으로 구간을 정함
- 관측 도구 container가 단계마다 다시 만들어져 단계 사이 지표가 없음
- Prometheus scrape 간격이 2초라 2초보다 짧은 Hikari 대기는 gauge에 보이지 않을 수 있음.
  이를 보완하기 위해 `hikaricp_connections_acquire_seconds_max`와 timeout 증가량을 함께 봄
- 카운터 기반 값(호출 수, connection 점유 합계)은 Prometheus `increase()` 외삽값임
- 30 req/s를 넘는 요청률은 사전 계획에 없어 실행하지 않음. B는 20 req/s까지만 실행함
- Grafana 외부 HTTP 패널은 요청률 series에 시간 단위 축을 사용해 y축이 잘못 표시됨(예: "3.33 mins").
  수치 판정은 Prometheus 쿼리 결과로 함

## 8. 증거

Prometheus 쿼리 결과는 `docs/perf/phase5b-results/<testid>.json`에 보존함. 각 파일에는 k6 종료 요약,
측정 구간, Hikari·Tomcat·CPU·JVM·서버·외부 HTTP 지표가 포함됨. A 파일은 계획 §11.2의 구간 정의(setup
제외)로 다시 추출한 버전이며, 이 문서의 모든 A 수치는 이 버전을 사용함. 이 구간 정의는 A 실행 약 10시간
뒤 B 실행 전에 등록했으므로 A에는 사후 적용임. 기존 정의와의 구간 차이는 1~2초이고 판정은 바뀌지 않음.

Grafana 고정 시간 범위 링크(로컬 Prometheus volume이 남아 있을 때 열림, retention 2일):

- A 1 req/s: <http://localhost:3000/d/travel-load?var-testid=phase5b-1rps-20261004-005647&from=1791043016000&to=1791043113000>
- A 3 req/s: <http://localhost:3000/d/travel-load?var-testid=phase5b-3rps-20261004-005958&from=1791043206000&to=1791043303000>
- A 5 req/s: <http://localhost:3000/d/travel-load?var-testid=phase5b-5rps-20261004-010152&from=1791043319000&to=1791043416000>
- A 10 req/s: <http://localhost:3000/d/travel-load?var-testid=phase5b-10rps-20261004-010339&from=1791043426000&to=1791043523000>
- A 20 req/s: <http://localhost:3000/d/travel-load?var-testid=phase5b-20rps-20261004-010526&from=1791043534000&to=1791043631000>
- A 30 req/s: <http://localhost:3000/d/travel-load?var-testid=phase5b-30rps-20261004-010716&from=1791043646000&to=1791043743000>
- B 링크는 각 결과 JSON의 `k6.grafana` 필드에 있음

k6 원본 로그(`planb-<testid>.log`)와 Grafana 화면 캡처(`<testid>.png`)는 Git에 넣지 않음. 작성자 로컬
보관 디렉터리(`Yeoro 프로젝트 모음/리팩토링 문서/텍스트 문서/phase5b-evidence/`)에 보존하며, 저장소
독자는 결과 JSON과 이 문서를 기준으로 확인함. 다중 계정 smoke 로그
(`phase5b-multiuser-smoke-20261004-110007`)도 같은 위치에 보존함. smoke의 `http_req_failed` 6/31은 다른 계정 조회에
대한 의도된 403 응답임.

각 실행 후 k6 teardown이 출력한 actuator 지표(`PLANB_METRICS_BEGIN` 구간)는 HTTP active 0, Hikari
pending 0이었음. Hikari active는 B 3 req/s 종료 시점만 1이고 나머지는 0이었음.

토큰 파일은 실행 스크립트가 매 실행 후 삭제함. 로그와 결과 JSON에 JWT·Bearer 문자열이 없음을 확인함.

## 9. 재현

1. MySQL·Redis를 시작함

```bash
docker run -d --rm --name planb-perf-mysql \
  -e MYSQL_DATABASE=planb_loadtest -e MYSQL_USER=planb -e MYSQL_PASSWORD=planb \
  -e MYSQL_ROOT_PASSWORD=planb-root -p 33306:3306 mysql:8.4
docker run -d --rm --name planb-perf-redis -p 36379:6379 redis:7.2
```

2. 스텁과 앱을 각각 별도 터미널에서 실행함

```bash
OPENAI_STUB_DELAY_MS=3000 ./gradlew travelLoadTestStubs

SPRING_PROFILES_ACTIVE=loadtest \
DATABASE_URL=jdbc:mysql://localhost:33306/planb_loadtest \
DATABASE_USERNAME=planb DATABASE_PASSWORD=planb \
REDIS_URL=redis://localhost:36379 \
JWT_SECRET=01234567890123456789012345678901 \
./gradlew bootRun
```

3. 다중 계정 하네스는 먼저 smoke로 사용자 간 격리를 확인함

```bash
TESTID="phase5b-multiuser-smoke-$(date +%Y%m%d-%H%M%S)" \
SCENARIO=smoke USER_COUNT=3 SMOKE_ITERATIONS=6 \
src/test/observability/run-observed-load.sh
```

4. 앱을 재시작하지 않고 1 → 3 → 5 → 10 → 20 → 30 req/s 순서로 실행함. 공유 계정은 `USER_COUNT`를
지정하지 않고, 다중 계정은 `USER_COUNT=200`을 추가함. 다른 요청률은 실험 계획 §3 표의
`PLAN_RATE`, `PRE_ALLOCATED_VUS`, `MAX_VUS`를 사용함. 아래는 다중 계정 20 req/s 예시임.

```bash
USER_COUNT=200 \
TESTID="phase5b-multiuser-20rps-$(date +%Y%m%d-%H%M%S)" \
SCENARIO=plan-throughput PLAN_RATE=20 PLAN_DURATION=60s \
PRE_ALLOCATED_VUS=160 MAX_VUS=400 GRACEFUL_STOP=30s \
src/test/observability/run-observed-load.sh
```
