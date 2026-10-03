# Phase 5A Travel AI 생성 트랜잭션 경계 검증

측정일: 2026-10-02  
기준: `7b85f38`의 Phase 4A-2 결과와 동일한 로컬 스텁 설정. Phase 5A 코드는 별도 worktree의 미커밋 변경으로 실행함.

## 검증 조건

- `loadtest` 프로필, MySQL 8.4, Redis 7.2, Docker 27.4.0을 사용함.
- OpenAI 정상 응답에 호출당 3초 지연을 적용함. Kor2·Kakao·영양 API는 로컬 스텁을 사용함. 실제 외부 API 성능은 측정하지 않음.
- 1 req/s와 3 req/s를 각각 60초간 `constant-arrival-rate`로 실행함. 앞선 1 req/s 실행을 3 req/s 실행의 warm-up으로 사용함.
- 실행 전에 3 req/s의 완료 기준을 Hikari pending 연속 4초 이하, pending 양수 샘플 총 2개 이하, connection 획득 대기 최대 1초, connection timeout 증가 0건으로 고정함.
- Hikari 시계열은 k6 시작부터 마지막 요청 완료까지 2초 간격으로 조회함. setup과 종료 후 지표 수집 구간은 제외함.

## 정확성 검증

`TravelValidationIntegrationTest` 27건과 `TravelLoadTestSmokeIntegrationTest` 1건이 모두 통과함. 생성 중 트랜잭션 부재와 저장 실패 시 전체 롤백을 포함함. Facade 단위 테스트와 빌드도 통과함.

## 부하 결과

| 지표 | 변경 전 1 req/s | 변경 후 1 req/s | 변경 전 3 req/s | 변경 후 3 req/s |
|---|---:|---:|---:|---:|
| 완료 iteration | 61 | 61 | 140 | 181 |
| drop / interrupt | 0 / 0 | 0 / 0 | 33 / 7 | 0 / 0 |
| 일정 생성 성공률 | 100% | 100% | 완료된 140건의 100% | 181건의 100% |
| 일정 생성 p95 | 6.593초 | 6.13초 | 31.486초 | 6.13초 |
| Hikari pending 최대 | 0 | 0 | 47 | 0 |
| Hikari pending 양수 샘플 | 0 | 0/34 | 약 84초 연속 | 0/33 |
| Hikari connection 점유 최대 | 6.663초 | 0.499초 | 6.685초 | 0.499초 |
| Hikari connection 획득 대기 최대 | 미기록 | 0.006초 | 미기록 | 0.010초 |
| Hikari connection timeout 증가 | 미기록 | 0 | 미기록 | 0 |

변경 후 3 req/s에서 pending은 모든 측정 샘플에서 0으로, 사전 완료 기준을 충족함. connection 점유 최대값은 변경 전 약 6.7초에서 0.5초 이하로 감소함. 3 req/s 일정 생성 p95는 31.486초에서 6.13초로 감소함. 고정된 OpenAI 대기 6초는 그대로 남아 있으므로 이는 외부 API 자체의 속도 개선을 뜻하지 않음.

## 증거와 한계

- k6 원본 로그: `/private/tmp/planb-phase5a-low.log`, `/private/tmp/planb-phase5a-pool.log`. 토큰 파일은 실행 스크립트가 삭제함.
- Grafana 1 req/s: <http://localhost:3000/d/travel-load?var-testid=phase5a-low-20261002&from=1790916757000&to=1790916854000>
- Grafana 3 req/s: <http://localhost:3000/d/travel-load?var-testid=phase5a-pool-20261002&from=1790916891000&to=1790916988000>
- 외부 HTTP와 OpenAI aggregate count는 scrape 경계의 영향을 받으므로 개별 API 호출 계약 증거로 사용하지 않음. 스텁 요청을 단언하는 통합 smoke 테스트를 회귀 근거로 사용함.
- 이번 결론은 로컬 정상 응답 스텁과 단일 생성 경로의 1·3 req/s 조건에 한정함. 실제 공급자 지연·장애, 편집 경로, 더 높은 요청률의 포화 자원은 검증하지 않음.

판정: Phase 5A의 로컬 정확성·성능 완료 조건을 충족함. Phase 5B에서는 같은 조건에서 다음 포화 자원을 다시 탐색해야 함.
