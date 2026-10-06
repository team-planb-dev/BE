# Phase 5B main 병합 후 독립 재측정

측정일: 2026-10-04  
대상: `main` merge commit `403b084405c1587028d82e7a655fdf3b0864d480` (PR #133)  
조건: JDK 21, Spring Boot `loadtest`, MySQL 8.4·Redis 7.2 로컬 컨테이너, 외부 API 스텁, OpenAI 호출당 고정 3초 지연

## 판정

- PR #133은 운영 코드 변경 없이 5B 실험 하네스·테스트·결과 문서를 병합한 작업. 플랫폼 스레드 1·3 req/s의 지연은 5B 전 기준선과 같은 수준.
- 같은 `main`의 36 req/s·400계정 비교에서 가상 스레드 p95 6.12초, 플랫폼 스레드 p95 10.05초. 가상 스레드의 p95가 39.1% 낮음.
- 플랫폼 실행은 Tomcat busy 200/200, k6 시작 누락 42건, Docker 경로 TCP 연결 실패 1건. 가상 스레드는 2,161건 모두 성공, 시작 누락 0건. 플랫폼의 연결 실패 원인은 확정되지 않았으므로 앱 오류로 단정하지 않음.
- Hikari timeout은 모두 0. 플랫폼 pending 최대 4개(2초 수집 1회), 가상 스레드 pending 0. 가상 스레드의 추가 동시 요청을 운영에 적용하려면 실제 외부 API 제한·메모리 조건 검증 필요.

## k6·Prometheus 결과

| 실행 | 완료/성공 | 누락 | 일정 생성 p95 | Tomcat busy 최대 | HTTP in-flight 최대 | Hikari pending 최대/timeout |
|---|---:|---:|---:|---:|---:|---:|
| 5B 전 1 req/s (#130) | 61/61 | 0 | 6.14초 | 7/200 | 7 | 0/0 |
| 병합 후 플랫폼 1 req/s | 61/61 | 0 | 6.17초 | 6/200 | 6 | 0/0 |
| 5B 전 3 req/s (#130) | 180/180 | 0 | 6.14초 | 18/200 | 18 | 0/0 |
| 병합 후 플랫폼 3 req/s | 180/180 | 0 | 6.14초 | 19/200 | 19 | 0/0 |
| 병합 후 플랫폼 36 req/s | 2,119/2,118 | 42 | 10.05초 | 200/200 | 200 | 4/0 |
| 병합 후 가상 36 req/s | 2,161/2,161 | 0 | 6.12초 | 해당 없음 | 222 | 0/0 |

36 req/s에서는 가상 스레드 환경변수 `SPRING_THREADS_VIRTUAL_ENABLED=true`, `SPRING_MAIN_KEEP_ALIVE=true`만 변경. 두 모드 사이에 앱·DB·Redis·스텁을 새로 시작. 1·3 req/s는 동일 플랫폼 JVM에서 순차 실행. k6 60초 일정 생성, 36 req/s는 사전 VU 300·최대 900·계정 400개. 부하 발생기와 서버가 한 장비를 공유하므로 결과를 Railway·실제 OpenAI 처리량으로 일반화하지 않음.

## 기술 선택의 근거와 병목 이동

4A의 3 req/s에서는 AI 응답을 기다리는 동안 넓은 트랜잭션이 DB 커넥션을 점유해 Hikari 풀이 먼저 포화됨. 5A는 조회와 원자적 저장만 각각 짧은 트랜잭션으로 묶고 AI 호출을 그 밖으로 옮김. 이 변경 뒤 같은 3 req/s에서 Hikari pending은 0, 일정 생성 p95는 31.49초에서 6.13초로 감소함. 순차 처리 자체가 Hikari 포화의 원인은 아니었음.

그다음 5B의 다중 사용자 부하에서는 플랫폼 요청 스레드가 36 req/s에서 Tomcat 한도 200개에 도달함. 같은 코드와 로컬 스텁 조건에서 가상 스레드를 켠 독립 재측정 결과, p95는 10.05초에서 6.12초로, k6의 시작 누락은 42건에서 0건으로 감소함. 1·3 req/s에서 플랫폼과 가상 스레드의 p95는 약 6.1초로 유사했으므로 단일 일정 생성 시간이 빨라졌다는 근거는 아님.

이 경로에서 `@Async`나 `CompletableFuture`로 HTTP 응답을 먼저 반환해도 완성된 일정의 도착 시간이 줄어드는 것은 아님. WebFlux/Reactor는 블로킹 JPA·JDBC 작업을 별도 스케줄러로 격리해 함께 사용할 수 있지만, 이벤트 루프에서 그대로 실행할 수는 없음. 현재의 동기 AI 호출, JPA 트랜잭션, 요청별 `ThreadLocal` 계약을 전면 변경할 실측 근거가 없어 그 전환은 이번 실험의 선택지가 아니었음. 가상 스레드는 해당 명령형 흐름을 유지하면서 지원되는 I/O 대기 중 carrier 플랫폼 스레드의 점유를 줄이는 방법으로 선택함. 코드를 논블로킹 방식으로 바꾼 것은 아님.

가상 스레드에도 요청별 스레드는 존재하며 Hikari 커넥션 수는 여전히 10개임. 트랜잭션이 AI 대기를 감싼 채였다면 carrier를 반납해도 DB 커넥션은 해제되지 않으므로 5A의 경계 분리가 선행 조건임. #131의 JFR에서 기록 임계값 20ms 이상의 `jdk.VirtualThreadPinned` 이벤트는 0건이었고, 이는 더 짧은 pinning이나 모든 운영 조건에서의 부재를 증명하지 않음. 실제 OpenAI·Railway 환경의 처리량과 외부 API 제한도 이 로컬 스텁 결과로 확정하지 않음.

## 증거

- Prometheus 요약 JSON: `docs/perf/phase5b-main-results/phase5b-main-*.json`
- 36 req/s k6 요약·오류 원문: `docs/perf/phase5b-main-results/phase5b-main-*-36rps-k6-summary.txt`
- 원본 k6 출력과 Grafana 캡처: 사용자 로컬 `phase5b-main-comparison-evidence/` 폴더. 비밀값 검출 없이 보존
- 이전 기준선: `docs/perf/phase5b-concurrency-result.md`, `docs/perf/phase5b-virtual-thread-result.md`
- Grafana 고정 시간 링크: JSON의 `grafana_url`. Prometheus 보존 기간 2일이 지나면 로컬 링크는 비어 있을 수 있음.

이번 독립 재측정에서는 JFR pinned 이벤트를 다시 수집하지 않음. #131의 JFR 결과는 별도 증거이며, 여기서는 k6·Prometheus·Grafana로 확인된 범위만 판정.
