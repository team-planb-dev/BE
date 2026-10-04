# Phase 5B 가상 스레드 on/off 비교 실험 계획

작성일: 2026-10-04  
Issue: [#131](https://github.com/team-planb-dev/BE/issues/131)  
상태: 실행 전 고정  
선행 결과: `docs/perf/phase5b-concurrency-result.md` (#130)

## 1. 목적

#130에서 Tomcat 요청 thread가 처음 사전 포화 기준에 도달했음(공유 계정 30 req/s, busy 182/200).
하지만 thread 200개가 모두 차지 않아 지연·실패 증가는 관측되지 않았음. Little's law로는 약 33 req/s에서
thread가 고갈될 것으로 예상됨.

이 실험은 다음 두 가지를 측정함.

1. 기본 platform thread에서 고갈 예상 지점(약 33 req/s)을 넘으면 무엇이 나빠지는가
2. 같은 조건에서 `spring.threads.virtual.enabled`만 바꾸면 결과가 달라지는가

결과로 가상 스레드 채택 후보 여부를 판정함. 운영 설정 변경은 이 이슈에서 하지 않음.

## 2. 단일 변수

| 모드 | 앱 실행 환경변수 |
|---|---|
| P (platform, 현재 기본) | 없음 |
| V (virtual) | `SPRING_THREADS_VIRTUAL_ENABLED=true`, `SPRING_MAIN_KEEP_ALIVE=true` |

`spring.main.keep-alive`는 Spring Boot 문서가 가상 스레드 사용 시 권장하는 설정임. 가상 스레드가 daemon
thread라 JVM이 일찍 종료되는 문제를 막을 뿐 요청 처리에는 관여하지 않음. Hikari, Tomcat 연결 설정,
스텁, k6 스크립트, `loadtest` 프로필은 두 모드에서 같음. 운영 설정 파일은 바꾸지 않음.

## 3. 고정 환경

- 기준 commit: `origin/dev` `dbf11b0ee37f252912176f5ee8e121581e2271c5`
- 장비·Docker·JDK·MySQL·Redis·Hikari 10·Tomcat max thread 200: #130 계획 §2와 같음
- OpenAI 스텁 호출당 3,000ms, 외부 API 스텁 지연 없음
- 앱은 `./gradlew bootRun`. 모드마다 MySQL·Redis container와 스텁, 앱을 새로 띄움
- 실행 순서: P 전체 단계 → V 전체 단계. 각 모드 안에서는 같은 JVM에서 단계를 오름차순으로 실행함

## 4. 부하 조건

- 하네스: 다중 계정 `USER_COUNT=400`. 42 req/s에서 예상 동시 요청(약 255개 이상)보다 많게 정함.
  대기열로 지연이 길어지면 같은 계정의 요청이 겹칠 수 있으며, 이는 기록만 함
- k6 `constant-arrival-rate`, 단계별 60초, `GRACEFUL_STOP=60s`

| 단계 | 요청률 | preAllocatedVUs | maxVUs | 의도 |
|---|---:|---:|---:|---|
| S1 | 20 req/s | 160 | 400 | warm-up 겸 기준 |
| S2 | 30 req/s | 240 | 600 | #130 A의 마지막 단계와 같은 요청률 |
| S3 | 36 req/s | 300 | 900 | 고갈 예상 지점 초과 |
| S4 | 42 req/s | 350 | 1,200 | 초과 폭 확대 |

#130의 진행 조건(성공률 100% 등)은 이 실험에서 쓰지 않음. P 모드에서 S3·S4가 나빠지는 것이 측정
목적이기 때문임. 대신 §6의 중단 조건에 걸리지 않으면 두 모드 모두 네 단계를 끝까지 실행함.

## 5. 측정 항목

- k6: 성공률, 실패 수와 실패 종류(HTTP 상태, `dial: i/o timeout` 등 연결 실패), p50·p95·p99, VU 최대,
  dropped iteration
- 서버: in-flight 최대, Tomcat busy·current(P 모드), Hikari active·pending·timeout·획득 대기 최대,
  process·system CPU, heap, JVM live thread
- 스텁 한계 확인: OpenAI 호출(`okhttp_requests`) 평균·최대 시간과 WebClient 오류. OpenAI 평균이 3.1초를
  넘으면 스텁 또는 연결 경로가 지연을 더한 것으로 보고 해당 단계를 `스텁·경로 영향 가능`으로 표시함
- 연결 경로: 단계 전후 `netstat -s -p tcp`의 `listen queue overflow` 증가량과 k6 연결 실패 수
- V 모드: JFR을 `jcmd <pid> JFR.start`로 단계 전체에 걸어 `jdk.VirtualThreadPinned` 이벤트 수와 최대
  지속시간을 기록함(기본 설정 임계값 20ms). P 모드에도 같은 방식으로 JFR을 걸어 기록 조건을 맞춤

측정 구간 정의는 #130 계획 §11.2와 같음(setup 요청 마지막 샘플부터 마지막 `k6_vus` 샘플까지).

## 6. 중단 조건

다음 중 하나가 15초 이상 지속되면 해당 단계를 중단하고 자료를 보존함. 같은 모드의 다음 단계는
실행하지 않음.

- system CPU 0.9 이상
- 일정 생성 HTTP 실패율 20% 이상
- 앱 또는 스텁의 비정상 종료, `up{job="travel-app"} == 0`
- heap 사용률 0.9 이상

## 7. 판정 기준

### 7.1 platform thread 고갈의 영향 (P 모드)

- S3·S4에서 Tomcat busy가 200에 도달하고, p95가 S1 p95보다 2초 이상 늘면 `thread 고갈로 지연 증가: 확인`
- busy가 200에 도달하지 않거나 p95 증가가 2초 미만이면 `증거 부족`
- 실패가 연결 실패(`dial: i/o timeout`)뿐이고 `listen queue overflow`가 증가하면 호스트 listen queue
  초과로, 증가하지 않으면 연결 경로(Docker Desktop) 쪽으로 기록함. 원인 분리가 안 되면 2단계로
  k6를 호스트에서 직접 실행해 재확인함(§9)

### 7.2 가상 스레드 채택 후보 판정 (P와 V 비교)

V를 채택 후보로 판정하려면 다음을 모두 만족해야 함.

1. S3와 S4 모두에서 V의 p95가 P보다 20% 이상 낮음
2. S1~S4 모든 단계에서 V의 성공률이 P 이상임
3. V의 Hikari connection timeout 증가 0, pending 양수 샘플 연속 2개(4초) 이하
4. V의 외부 API·OpenAI 오류가 P보다 많지 않음
5. V의 heap 사용률 최대가 P보다 10%p 이상 높지 않음
6. JFR `jdk.VirtualThreadPinned` 이벤트가 0건이거나, 있더라도 요청 처리 경로가 아님을 stack trace로
   확인함

하나라도 어기면 채택 후보로 판정하지 않음. S1·S2처럼 thread가 고갈되지 않은 단계의 단일 요청 지연
차이는 채택 근거로 쓰지 않음.

## 8. 실행 전 예측

측정값이 아니라 판정 이후 비교를 위한 기록임.

- P S1·S2: #130과 같이 p95 약 6.1초, 실패 0
- P S3(36 req/s): 고갈 이후 초당 약 3건이 대기열에 쌓여 60초 동안 약 180건이 밀림. p95는 약 10초
  안팎으로 늘 것으로 예측함
- P S4(42 req/s): 초당 약 9건이 쌓여 p95가 20초 안팎까지 늘 것으로 예측함. k6 기본 요청 timeout 60초
  안에서는 실패보다 지연 증가가 먼저 나타날 것으로 예측함
- V: thread 상한이 없으므로 S3·S4에서도 p95가 약 6.1초에 머물 것으로 예측함. 평균 점유 Hikari
  connection은 42 req/s에서도 1개 안팎으로 예측함
- V의 JVM live thread는 P보다 적을 것으로 예측함
- pinned 이벤트는 앱 코드에 `synchronized`가 없으므로 적을 것으로 예측하지만, 의존 라이브러리 때문에
  0건이라고 예측하지는 않음

## 9. 연결 경로 2단계 확인

P 또는 V에서 연결 실패가 다시 나오고 §7.1로 원인이 분리되지 않으면, 같은 단계 하나를 k6 호스트 실행으로
반복함. 이 실행은 원인 분리용이며 P/V 비교 표에는 넣지 않음. 호스트용 k6 설치 여부는 이때 사용자에게
확인함.

## 10. 증거 보관

- testid: `phase5b-vt-<p|v>-<rate>rps-<시각>`
- k6 로그, Prometheus 쿼리 결과, JFR 요약, `netstat` 전후 값, Grafana 고정 시간 범위 링크와 캡처를 보존함
- JFR 원본(`.jfr`)과 앱 로그는 Git에 넣지 않음
