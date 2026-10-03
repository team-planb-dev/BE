# Phase 5B 다중 사용자 동시 요청 포화 자원 실험 계획

작성일: 2026-10-04  
Issue: [#130](https://github.com/team-planb-dev/BE/issues/130)  
상태: 실행 전 고정

## 1. 목적

Phase 5A 최종 병합 코드에서 일정 생성 요청을 요청률별로 늘릴 때 처음 포화되는 자원을
판정함. 이 문서의 값은 결과를 보기 전에 고정하며, 실행 중 변경하지 않음.

측정 전에는 가상 스레드, 요청 내부 병렬화, Hikari pool 확대를 적용하지 않음.

## 2. 고정 환경

| 항목 | 값 |
|---|---|
| 기준 commit | `origin/dev` `a23080ab4fa036c5987389a376b916b0fc4a0751` |
| 장비 | Apple M1 Pro, 8 core, 16 GiB, macOS 15.7.7 |
| Docker | 27.4.0, VM 8 CPU, 7.65 GiB |
| JDK | 21.0.2, Gradle toolchain 21 |
| MySQL | 8.4.10 container (`planb-perf-mysql`, 33306) |
| Redis | 7.2.16 container (`planb-perf-redis`, 36379) |
| Hikari 최대 connection | 10 (설정 override 없음, 실행 중 `hikaricp_connections_max`로 확인) |
| Tomcat 최대 thread | 200 (설정 override 없음, 실행 중 `tomcat_threads_config_max_threads`로 확인) |
| 프로필 | `loadtest` |
| 앱·스텁 | 앱과 스텁은 호스트 JVM, k6·Prometheus·Grafana·MySQL·Redis는 Docker |

앱, 스텁, 부하 발생기, DB가 같은 장비를 공유함. CPU와 메모리 판정은 이 공유를 전제로 함.

## 3. 부하 조건

- 경로: `POST /api/v1/travel/add-with-recommend`만 측정함
- k6 `constant-arrival-rate`, 단계별 60초, `GRACEFUL_STOP=30s`
- k6 `setup()`이 계정 1개와 동행인 1명을 만들고 모든 VU가 같은 JWT를 사용함.
  따라서 측정 대상은 사용자별 상태가 아니라 **요청 간 동시성**임
- OpenAI 스텁: 정상 응답, 호출당 고정 3,000ms, 일정 1건당 2회 호출
  (`TravelLoadTestSmokeIntegrationTest` 단언 기준)
- Kor2·Kakao Map·Kakao Mobility·영양 API: 지연 없는 로컬 스텁
- 실제 외부 API 키와 유료 호출은 사용하지 않음

| 단계 | 요청률 | preAllocatedVUs | maxVUs | 비고 |
|---|---:|---:|---:|---|
| S1 | 1 req/s | 10 | 20 | warm-up 겸 기준선 |
| S2 | 3 req/s | 24 | 60 | Phase 5A 재현 |
| S3 | 5 req/s | 40 | 100 | 탐색 |
| S4 | 10 req/s | 80 | 200 | 탐색 |
| S5 | 20 req/s | 160 | 400 | 탐색 |
| S6 | 30 req/s | 240 | 600 | 탐색 |

maxVUs는 Little's law의 예상 동시 요청 수(요청률 × 약 6.5초)의 2배 이상으로 정함.
VU 상한 때문에 생긴 drop을 서버 포화로 오인하지 않기 위해서임.

## 4. 측정 구간

- 앱 지표에는 `testid` 라벨이 없음. 측정 구간은 해당 `testid`의 k6 지표가 처음 기록된
  시각부터 마지막 요청 완료 시각까지로 정함
- k6 `setup()`과 실행 후 drain 수집 구간은 판정에서 제외함
- 각 단계 종료 후 HTTP active 요청 0, Hikari active·pending 0을 확인한 뒤 다음 단계를 시작함
- Prometheus scrape 간격은 2초임

## 5. 다음 단계 진행 조건

아래를 모두 만족할 때만 다음 요청률로 진행함.

- 일정 생성 성공률 100%, dropped iteration 0, interrupted iteration 0
- Hikari pending 양수 샘플이 연속 2개(4초) 이하이고 connection timeout 증가 0
- Tomcat 요청 connector busy/max 0.8 미만
- process CPU와 system CPU 0.8 미만
- 외부 HTTP 4xx·5xx 및 client 오류 0
- 일정 생성 p95가 S1 p95의 1.5배 이하

## 6. 중단 조건

다음 중 하나가 15초 이상 지속되면 해당 단계를 중단하고 자료를 보존함.
스크립트는 자동 감시하지 않으므로 실행자가 Prometheus 쿼리로 확인함.

- system CPU 0.9 이상
- 일정 생성 HTTP 실패율 20% 이상
- Hikari pending과 일정 생성 지연이 함께 지속 증가
- 앱 또는 스텁의 비정상 종료, `up{job="travel-app"} == 0`

## 7. 포화 판정 기준

| 자원 | 포화 판정 | 근거 지표 |
|---|---|---|
| Hikari | pending > 0이 10초 이상 지속되거나 connection timeout 증가 | `hikaricp_connections_pending`, `hikaricp_connections_timeout_total` |
| Tomcat 요청 thread | 8080 connector busy/max ≥ 0.8이 10초 이상 지속 | `tomcat_threads_busy_threads / tomcat_threads_config_max_threads` |
| CPU | process 또는 system CPU ≥ 0.8이 10초 이상 지속 | `process_cpu_usage`, `system_cpu_usage` |
| JVM 메모리 | heap 사용률 ≥ 0.85가 10초 이상 지속 | `jvm_memory_used_bytes`, `jvm_memory_max_bytes` |
| 외부 HTTP | 외부 API 또는 OpenAI client 오류·timeout 발생 | `http_client_requests_seconds_count`, `okhttp_requests_seconds_count`의 status·exception |
| 부하 발생기 | VU가 maxVUs에 도달한 상태의 drop | `k6_vus`, `k6_dropped_iterations_total` |

10초 지속 기준은 Phase 4A-2의 포화 검출 기준과 같음. `dropped iterations`만으로 Tomcat
포화라고 판정하지 않음.

## 8. 실행 전 예측

측정값이 아니라 판정 이후 비교를 위한 기록임.

- 요청 1건의 Tomcat thread 점유는 OpenAI 3초 × 2와 처리 시간을 합한 약 6.2~6.5초임.
  Little's law로 예상 동시 요청은 요청률 × 6.5초임
- Tomcat busy/max 0.8은 약 25 req/s에서 도달하고, 30 req/s에서는 약 195개로 200에 근접함.
  따라서 Tomcat은 S6에서 처음 포화될 것으로 예측함
- Hikari는 요청당 짧은 트랜잭션 2개만 사용함. S1·S2에서 측정한 평균 점유 시간 × 요청률이
  10에 크게 못 미치면 S6까지 포화되지 않을 것으로 예측함
- CPU는 Phase 4A-2 3 req/s에서 process 최대 6.7%였음. 요청당 비용이 선형이라면
  30 req/s에서도 0.8에 도달하지 않을 것으로 예측함. 단, k6·DB·스텁이 같은 장비를
  공유하므로 system CPU는 별도로 판정함

## 9. 판정 후 행동

- 목표 요청률에서 선행 포화가 없으면 추가 운영 코드 없이 5B를 완료함
- DB가 먼저 포화되면 query·transaction·pool capacity 근거를 분리해 기록함.
  pool 크기만 즉시 늘리지 않음
- 외부 HTTP 대기·오류 증거가 있으면 해당 pool 계측을 후속으로 분리함
- Tomcat 요청 thread가 먼저 포화되고 CPU·DB·HTTP pool에 여유가 있을 때만 가상 스레드
  on/off 단일 변수 실험을 후속 이슈로 분리해 검토함
- 요청 내부 병렬화(5C)는 이번 이슈에 포함하지 않음

## 10. 증거 보관

- 파일명에 `phase5b`, 요청률, `testid`를 포함함
- k6 원본 출력, Prometheus 쿼리 결과, Grafana 고정 시간 범위 링크를 보존함
- 토큰 파일, 인증 header, 앱 원본 로그는 Git에 넣지 않음
