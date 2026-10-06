# Phase 5C 영양 조회 병렬화 전후 부하 측정

측정일: 2026-10-06
코드 기준: `6d23bcd`와 동일한 미커밋 단계 계측. Before는 Claude의 영양 병렬화 patch만 역적용, After는 patch 적용.
판정 범위: 로컬 고정 지연 스텁의 응답 시간과 회귀. 실제 영양 API 처리량·요금·제한 검증 제외.

## 측정 계약

- 두 상태를 별도 임시 복사본으로 빌드. 비교 전 새 MySQL·Redis와 새 앱 기동. 기존 작업 트리의 미커밋 파일 보존
- 가상 스레드 활성화, 동행인 200명, 점심 12시·당뇨병 설정. 이틀 일정 생성 3 req/s, 60초, 사전 VU 90·최대 180
- OpenAI 스텁 호출당 3초, 영양 API 스텁 조회당 4초. 두 상태에서 동일한 k6 옵션과 스텁 응답 사용
- 원래 k6 동행인은 식사 설정이 꺼져 있어, 양쪽 비교 복사본에 동일하게 `COMPANION_LUNCH=true` 옵션을 추가. 측정 뒤 원래 기본값을 유지하는 선택 옵션을 작업 트리에도 반영. Smoke에서 영양 조회 2건/요청 확인
- 동일 상태 3회 반복. 각 실행의 setup 계정 생성은 k6 `plan_duration`에 포함하지 않음. Grafana 링크에는 setup·drain이 보이나 판정은 k6 일정 생성 요약과 단계 Counter 증가량으로 수행
- 각 요청의 영양 메뉴는 2개. `NutritionService` 현행 정책상 영양 조회 결과는 `NOT_EVALUABLE`이며 `UNAVAILABLE`은 0건

## 결과

| 상태 | 반복 | 완료한 일정 | 일정 생성 p95 | 영양 보강 평균 | 영양 조회 | 성공률 | Hikari pending 최대 |
|---|---:|---:|---:|---:|---:|---:|---:|
| 순차 | 1 | 181 | 14.17초 | 8.014초 | 362 | 100% | 0 |
| 순차 | 2 | 180 | 14.21초 | 8.012초 | 360 | 100% | 0 |
| 순차 | 3 | 180 | 14.15초 | 8.009초 | 360 | 100% | 0 |
| 병렬 2건 | 1 | 180 | 10.14초 | 4.005초 | 360 | 100% | 0 |
| 병렬 2건 | 2 | 181 | 10.10초 | 4.004초 | 362 | 100% | 0 |
| 병렬 2건 | 3 | 181 | 10.13초 | 4.006초 | 362 | 100% | 0 |

세 번의 p95 중앙값은 **14.17초 → 10.13초**, 4.04초·28.5% 감소. 영양 보강 평균 중앙값은 **8.012초 → 4.005초**, 약 절반. 순차 p95 범위 14.15~14.21초와 병렬 범위 10.10~10.14초는 겹치지 않음.

모든 실행에서 검사 실패·HTTP 실패·dropped iteration·Hikari connection timeout 0건. 영양 조회는 정확히 **요청당 2회**, OpenAI **2회**, 외부 WebClient **15회**로 전후 동일. 영양 평가 상태는 모두 `NOT_EVALUABLE`로 동일. `nutrition_lookup` Timer는 두 조회의 시간을 각각 세므로 병렬 상태의 조회 시간 **합계**를 영양 보강 wall-clock과 직접 비교하지 않음.

병렬 코드의 독립 재검증: `./gradlew clean test` 성공. 122개 테스트 클래스의 **804건 통과**, 실패·오류·skip 0건. 유료 외부 API 테스트는 실행하지 않음.

## 판정과 한계

**로컬 성능 효과 확인.** 고정된 4초짜리 두 영양 조회가 겹치면서 전체 일정 생성 p95가 반복 측정에서 약 4초 감소. 추가 외부 호출, 결과 상태 변화, 로컬 DB 대기·오류는 관측되지 않음.

**실제 공급자 처리량과 운영 병렬 적용은 미검증.** 요청당 동시성 2는 여러 사용자 요청을 합친 전역 제한이 아님. 실제 계정의 초당·동시 허용량은 확인되지 않았고, 식약처 응답 코드 23은 현재 `UNAVAILABLE` 원인과 구분되지 않음. 이 실험은 스텁이므로 제한 초과 가능성을 배제하지 못함. `NOT_EVALUABLE`은 현행 영양 평가 정책이며, 이번 결과를 식사 평가 품질 개선으로 해석하지 않음. 편집 경로의 별도 부하 비교도 수행하지 않음. 따라서 `common-prod`에서는 `NUTRITION_LOOKUP_CONCURRENCY` 기본값을 1로 유지하며, 공급자 한도와 오류 구분을 확인한 뒤에만 2로 변경 가능. 로컬 부하 측정은 동시성 2 설정을 사용.

## Grafana 고정 시간 링크

| 상태 | 반복 | 링크 |
|---|---:|---|
| 순차 | 1 | [before r1](http://localhost:3000/d/travel-load?var-testid=phase5c-before-3rps-r1-20261006&from=1791266440000&to=1791266588000) |
| 순차 | 2 | [before r2](http://localhost:3000/d/travel-load?var-testid=phase5c-before-3rps-r2-20261006&from=1791266597000&to=1791266744000) |
| 순차 | 3 | [before r3](http://localhost:3000/d/travel-load?var-testid=phase5c-before-3rps-r3-20261006&from=1791266747000&to=1791266893000) |
| 병렬 | 1 | [after r1](http://localhost:3000/d/travel-load?var-testid=phase5c-after-3rps-r1-20261006&from=1791267105000&to=1791267252000) |
| 병렬 | 2 | [after r2](http://localhost:3000/d/travel-load?var-testid=phase5c-after-3rps-r2-20261006&from=1791267260000&to=1791267403000) |
| 병렬 | 3 | [after r3](http://localhost:3000/d/travel-load?var-testid=phase5c-after-3rps-r3-20261006&from=1791267405000&to=1791267547000) |

Grafana의 기존 대시보드는 k6 일정 지연·DB·JVM 지표 표시. 영양 단계 수치는 Prometheus의 `planb_travel_plan_stage_seconds_{count,sum}`에서 실행별 Counter 증가량으로 집계. Prometheus 보존 기간은 현행 Compose 설정상 2일이며, 대시보드 링크는 로컬 서비스 실행 중에만 접근 가능.

## 증거와 재현

- 집계 JSON: `phase5c-grafana-metrics-20261006.json`
- 원본 Prometheus 조회 시계열: `phase5c-grafana-raw-series-20261006.json`. k6 p95·영양 단계 Timer·영양 결과·Hikari pending을 2초 간격으로 보존
- 원본 k6 로그와 집계 스크립트: `/private/tmp/planb-phase5c-grafana-20261006/`의 실행별 `.log`, `collect_metrics.py`
- 실행 스크립트: `src/test/observability/run-observed-load.sh`. 작업 트리에는 `COMPANION_LUNCH` 전달 옵션을 추가하되 기본 false 유지
- 앱·DB·Redis·스텁은 측정 종료 후 정리. Prometheus와 Grafana 컨테이너·데이터 volume은 결과 확인을 위해 유지
