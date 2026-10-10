# P2-4C 실제 API 후보 선조회 독립 실험 결과

작성일: 2026-10-10 · Issue #173 · **판정: 채택 기준 미달, dev/main 병합 보류**

## 요약

P2-4A 기준 생성 검색 Tool 두 개를 Java 선조회로 옮긴 독립 실험. 실제 생성 12회 중 11회 성공. 성공 생성 중앙값 23.889초로 P2-4A보다 약 19.5% 증가, token 중앙값 35,232로 약 26.5% 감소. 호출량·token 감소를 지연·품질 개선으로 간주하지 않고 채택 보류.

## 실행 조건과 비교 범위

- 코드 기준: P2-4A commit `2a99375f1316b2652bf6965e61e2f21a4665afde`. P2-4B 구현 제외.
- 이번 코드: #173 브랜치 미커밋 상태. 실행 전 파일별 SHA256은 `evidence/p2-4c-20261010/actual-api-run-manifest.json`에 보존.
- 실제 runId: `2026-10-10T10-17-17.413692Z`. 서울·부산·강릉·경주 4case×3회, case·repeat·seed 대응 확인. 모델 `gpt-5.6-terra`, reasoning `none`, 생성 전용, 편집 호출 0회.
- DB·Redis Testcontainers 격리. 첫 회차 cache miss, 이후 회차 cache 재사용. 첫 회차는 관광지·음식점·상세 준비와 외부 API 지연을 포함.
- P2-4A runId `2026-10-08T05-45-48.083415Z`의 보존 JSONL 12개와 비교. 서로 다른 날짜·소표본·모델 비결정성·공급자 응답 지연의 영향 포함. 고정 keyword와 후보 상한으로 입력 분포도 변경.
- 사용자 승인 12회 batch 1회 실행. 재측정·추가 paid batch 없음.

## 실제 결과

| 지표 | P2-4A | P2-4C | 해석 |
|---|---:|---:|---|
| 생성 통과 | 12/12 | 11/12 | R0 대비 1건 감소는 허용 폭 이내, 별도 지연·하드 gate 미달 |
| 성공 생성 중앙값 | 19.986초 | 23.889초 | 약 19.5% 증가, 목표 13.990초 이하 미달 |
| 성공 token 중앙값 | 47,916.5 | 35,232 | 약 26.5% 감소, 비용 gate 충족 |
| 전체 시도 LLM 호출 | 61 | 60 | 실패 요청 포함, 총 호출 차이 작음 |
| 재선택 단계 실행 | 1 | 6 | 검색 제거 이후 재선택 왕복 증가 |
| 지정 장소 포함 | 12/12 | 11/12 | 실패 응답 포함 |
| 날짜 간 장소 중복 | 0 | 0 | 유지 |
| 알레르기 키워드 추정 | 0 | 1 | 기존 하드 gate 미달, 성분 확정 의미 아님 |
| 메뉴 출처 일치 | 72/72 | 66/66 | 성공 결과의 확인 슬롯만 집계 |
| 영양 채움 | 36/72 | 33/66 | R0 대비 감소 10%p 이내 |
| CAFE_REST 슬롯 | 11 | 6 | R0 4개 기준 감시 허용 폭 이내 |
| 관광지 태그 합계 | 72 | 71 | 슬롯 분모 72→66, 슬롯당 비율로 R0 감시 gate 통과 |
| 실행당 이동시간 중앙값 | 300분 | 291분 | 성공 건 기준, R0 감시 허용 폭 이내 |

| 지역 | P2-4A 성공 중앙값 | P2-4C 성공 중앙값 | 방향 |
|---|---:|---:|---|
| C1 | 21.739초 | 23.889초 | 증가 |
| C2 | 15.170초 | 16.806초 | 증가 |
| C3 | 20.022초 | 23.995초 | 증가 |
| C4 | 19.950초 | 31.207초 | 증가 |

지역별 중앙값은 4개 모두 증가. 경주는 성공 2건 기준이며, 실패 1건은 지연 중앙값에서 제외하고 전체 12회 시도와 품질 실패 판정에는 포함.

P2-4C 반복 회차별 성공 중앙값: 1회차 50.752초, 2회차 23.889초, 3회차 14.322초. 이 차이를 코드 효과만으로 설명하지 않고 cache warm-up·모델·공급자 영향을 포함한 관찰로 기록.

## 채택 gate

| 기준 | 결과 |
|---|---|
| passCountR0 | 통과 |
| hardViolationZero | 미달 |
| plannedPlaceR0 | 통과 |
| medianAtMost70PercentOfP24A | 미달 |
| tokensAtMost110PercentOfP24A | 통과 |
| atMostOneSlowerCase | 미달 |

측정 가능한 감시 gate는 모두 통과. HIGH 비율·영업시간 불일치는 기존 기준선 미측정으로 N/A. 통과한 것으로 바꾸지 않음. 전체 gate 미달로 실험 코드 commit·push·PR·dev/main 병합 보류.

## 실패와 지연 분석

### 확인 사실

- 경주 C4 2회차: `PLAN.EXCEPTION.INVALID_AI_PLACE`, `이동시간 누락 또는 유효하지 않음`으로 저장 거부. `ai_response` 약 11.769초, `place_validation` 약 0.013초는 성공. `finish_plan` 약 0.388초에서 실패. AI upstream 인증 실패와 구분. 정확한 누락 구간·좌표·경로 응답 원인은 현재 기록만으로 미확정.
- 부산 C2 3회차: 음식점 `해운대해물칼국수`, 메뉴 `해물칼국수`의 `해물`이 기존 알레르기 추정 지표에 적중. 실제 새우 성분 포함 여부는 미확인. 실행 후 임의로 적중을 삭제하거나 gate 완화 없음.
- 서울 C1 1회차: 총 94.453초, LLM 호출 22회, token 225,093. `ai_response` 약 58.47초, `place_validation` 약 34.49초이며 그 안의 `reselect` 약 31.65초. `finish_plan` 약 1.29초. 부모·자식 Timer 중첩이므로 전부 합산하지 않음.
- 전체 재선택 실행 1회→6회. 최초 지역 검색 Tool 제거만으로 최종 검증 재선택 왕복을 제거하지 못한 실행 증거.
- 측정 구간 TourAPI 응답 113건, 전부 `ok`. endpoint별 areaBasedList2 4, areaCode2 42, detailIntro2 39, searchKeyword2 28. 종료 후 메뉴 출처 확인용 호출은 측정 count 밖이므로 공급자 전체 사용량으로 해석하지 않음.
- Gradle externalTest는 BUILD SUCCESSFUL, JUnit 12건·실패/오류/건너뜀 0. 관찰 테스트는 API 실패도 결과로 기록하므로 JUnit 성공과 일정 생성 12/12 성공은 별개.

### 가능한 설명과 미확정 요소

후보를 미리 제공하면 초기 검색 왕복을 줄일 수 있으나, 후보 JSON 입력량과 후보 선택의 난도가 달라지고 검증 이후 재선택이 추가될 수 있음. 서울 최초 실행에서 이 경로가 지연·token 증가에 기여한 사실은 확인. 후보 선조회가 이를 직접 유발했는지, 외부 응답 지연이나 모델의 탐색 선택 때문인지는 대조 실험 없이 확정 불가.

`ai_response` Timer에는 선조회도 포함. 선조회·남은 Tool 네트워크·각 LLM 호출의 순수 wall-clock 기여를 전부 독립 계측한 결과가 아니므로, 부모 시간에서 LLM 합을 뺀 값을 선조회 시간으로 확정하지 않음. 개선 방향을 보기 위해 무료 스텁 p95를 실제 AI 효과로 대체하지 않음.

## P2-4B 실패에서 함께 확인한 교훈

B는 P2-4A 대비 token 약 53.4% 감소, 생성 9/12·성공 중앙값 27.194초로 미달. 전체 모델 호출 61→111, 재선택 1→13. 부산 3회는 `재선택 메뉴 원본 불일치`로 거부. Java가 준비한 메뉴 후보와 legacy 재선택의 자유 메뉴 출력 사이 계약 차이는 코드상 위험이지만, 어떤 `accepts` 조건이 각 실패를 만들었는지는 미확정.

C는 B를 누적하지 않고 검색 제거만 시험. 생성 성공 수는 B보다 많지만 둘을 같은 조건의 직접 원인 대조군으로 주장하지 않음. B와 C 모두 token 감소만으로 종단 지연·품질 개선이 보장되지 않음을 관찰. 다음 후보를 고르기 전 최초 생성→검증→재선택의 후보·메뉴 계약과 남은 왕복을 구분할 필요.

## 결과 검토

독립 증거 검토 9.3/10, Critical·Important 지적 0건. 원본 A/C 24행·집계·OpenMetrics·대시보드 쿼리 값 대조 완료. 문서 검토 통과와 실험 채택 gate 미달은 별도 판정.

## Grafana와 증거

- [실제 API 전후 비교 대시보드](http://localhost:3000/d/p24c-actual-api/8fd838a)
- 13개 패널: 성공 시간·token 중앙값, 통과 수, 지역·실행별 시간, LLM 호출, 카페·영양·품질, 단계별 누적시간(해당 단계가 기록된 요청의 평균, 실패 포함). 전용 Prometheus `http://localhost:9091`, 데이터 소스 `p24c-actual`.
- **실제 JSONL을 OpenMetrics로 변환해 표시한 관찰 대시보드**. 실시간 Actuator scrape·k6 throughput·운영 p95가 아님. 시계열 시각은 batch 시작으로 색인하며 요청별 시작 시각을 재현하지 않음.
- 빈 전용 TSDB에 `promtool tsdb create-blocks-from openmetrics`로 가져온 뒤 기동. 기존 부하 Prometheus 데이터와 분리. 저장 경로 `/private/tmp/p24c-grafana/data`, retention 30d. 임시 경로·로컬 링크는 영구 증거 아님.
- 모든 데이터 패널 쿼리 12개가 비어 있지 않음 확인. 성공 12/11·중앙값 19.986/23.889가 집계 원본과 일치. Grafana 화면에서도 확인.
- 재현 데이터: `p2-4c-actual-api-measurements-20261010.json`, `evidence/p2-4c-20261010/actual-api-comparison.openmetrics`, `grafana-actual-api-comparison.json`, `grafana-query-validation.json`, 실행 manifest.
- 실제 원본 JSONL·응답·실행 로그는 프로젝트 build와 지정 보관 사본에 유지, Git 제외. 키·인증값은 점검/마스킹 후 로그 보존.
- 결과·설계·집계·대시보드 사본은 `/Users/wooju-kang/Desktop/개발/Yeoro 프로젝트 모음/리팩토링 문서/텍스트 문서`에 동기화. `refactoring-plan.md` 스테이징 금지 유지.

공식 표시 방식 근거: [Prometheus OpenMetrics 가져오기](https://prometheus.io/docs/prometheus/latest/storage/#backfilling-from-openmetrics-format), [Prometheus exposition](https://prometheus.io/docs/instrumenting/exposition_formats/), [Grafana Dashboard API](https://grafana.com/docs/grafana/latest/developer-resources/api-reference/http-api/dashboard/). 성능 수치 근거는 실제 실험 원본이며 공식 문서에서 유추한 값이 아님.

## 실험 코드 보존 상태 (2026-10-10 추가)

사용자 요청에 따라 실험 코드·테스트를 별도 커밋 `1ab45bb`으로 보존. 위 미커밋 표기는 측정 당시 상태. 후보 실험은 draft PR로 기록하고 운영 기준은 P2-4A 유지. 결과 문서의 dev 반영은 실험 코드 채택 또는 품질 문제 해결을 뜻하지 않음. 이번 작업의 main 동기화 없음. 실제 호출 추가 없음.
