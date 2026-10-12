# Part 2 후속 2단계 — 공원 후보 자격과 실패 슬롯 복구

작성일: 2026-10-11  
관련 Issue: [#179](https://github.com/team-planb-dev/BE/issues/179)  
상태: 로컬 구현·무료 회귀 929건·실제 API 생성 12회·Grafana 관측 완료. 측정한 주 gate·감시 기준 충족, 전체 품질 해결 판정 아님. dev PR 전달, main sync·운영 배포 미실시

## 1. 범위와 기준

Part 2 선택 계약 후속 순서의 **2단계**이며 P2-2 재기준선 단계와 구분. 기준 코드는 #177의 dev 병합 commit `f236f953f6ca3dd55cbb67f1e20142c0fe4ec280`, tree `8624362e2467e12a80c821ef623e92df367e579e`. 작업 위치는 `/private/tmp/planb-stage2-179`, 브랜치는 `codex/179-refactor-align-travel-park-eligibility-and-failure-local-recovery`.

목표는 잘못된 후보의 성공 승인 방지와 실패 슬롯에 한정한 복구. 실제 모델 성공률·장소 유형 선택률 개선은 별도 평가 대상. HIGH 1회분량·영업시간·실제 재료 안전성은 이번 변경으로 해결 판정하지 않음. Facade·트랜잭션·DB schema·REST/STOMP 응답 형식·라이브러리 의존성 변경 없음.

설계: `p2-selection-quality-followup-design.md`. 사전 근거: `p2-selection-quality-followup-research.md`. 이번 공식 자료 조사: `p2-selection-recovery-stage2-research.md`.

## 2. 재현한 문제와 변경

| 경계 | 변경 전 반례 | 변경 후 계약 |
|---|---|---|
| PARK_WALK 검색 | AT4 목록의 첫 호수를 성공 반환, 같은 목록의 뒤쪽 공원 유실 | 대표 후보·경로 선택 전에 원본 공원 분류로 필터. 적격 후보 없음은 found=false·미등록 |
| 초기 메뉴 출처 오류 | 정상 관광지까지 포함한 생성 응답을 callback 교정으로 다시 요청 | 구조 검증 유지, 메뉴 출처는 최종 resolver와 기존 슬롯 재선택 경로로 전달 |
| 시간 보정 뒤 식사 보충 | 잘못된 원본 위도 181인 식사를 최종 결과로 반환 | 새 식사에 기존 출처·유형·중복·좌표 검사 적용. 누락·NaN·범위 오류 거부 |
| 부분 날짜 편집 | 1·2일차 finish에서 예약 밖 3일차 식당·메뉴를 1일차 아침으로 다시 삽입 | finish 대상 밖 저장 날짜의 장소·메뉴를 보충과 최종 검증 양쪽에 예약 |

### 공원 후보

`ParkPlacePolicy`가 현재 Java의 `AT4/12`와 `categoryName.contains("공원")` 계약 공유. `PlanPlaceResolver`의 source별 유형 guard 유지. Kakao 상세 분류가 null·빈 값·호수이면 공원 후보에서 제외. 이름에 공원이 있다는 이유로 승인하지 않음. ATTRACTION·MUST_HAVE의 일반 관광지 자격 유지.

`PlanTourismTool → TourismTool → KakaoMapServiceHandler → KakaoPlaceSearchHelper`에 공원 필터 전달. 기존 공개 Tool signature 유지, 새 overload에는 @Tool 등록 없음. 이미 받은 목록만 필터하므로 검색 횟수 추가 없음. 앞 후보가 이미 사용된 공원이면 같은 목록의 다음 미사용 공원 선택.

### 메뉴 복구

`TravelRecommendHandler`의 초기 callback에서 메뉴 출처 오류 수집 제거. 이는 검증 경계 이전이며 출처 검사 삭제가 아님. 일수·관광지 구조 검사, JSON 파싱·refusal·upstream 실패 처리 유지. `PlanService`의 기존 `recoverPlace`·정상 슬롯 선예약·최대 두 번 재선택 재사용. 새 전역 retry·실패 enum·Advisor·별도 복구 프레임워크 도입 없음.

정상 관광지의 candidateId·선택값 보존. 식당이 바뀌어 파생 이동시간·시각·복약·태그가 다시 계산되는 것은 보존 위반으로 취급하지 않음. 실제 LLM 왕복·token 감소는 이번 fixture로 판정하지 않음.

### 늦은 식사 보충과 편집 보존

`refillMissingMeals`의 결과를 다시 정규화한 뒤 새 식사만 기존 resolver로 승인. 원래 식사는 dayNumber·scheduleType·candidateId별 개수로 구분. 기존 편집 슬롯의 provider 재조회·재선택 강제 제외. 이 면제는 현재 completer가 기존 식사 선택을 교체하지 않고 normalizer가 시각을 보정하는 계약에 한정.

부분 편집에서는 `currentPlan` 중 이번 finish 대상 밖 날짜만 장소·메뉴 예약. 바뀌는 날짜의 예전 슬롯은 예약하지 않아 정상 재구성 차단 방지. 보충과 신규 검증에 같은 예약 전달. 마지막 필수 식사·복약 검증 유지.

## 3. Research와 grilling 결정

Kakao 공식 Local 명세에서 AT4는 관광명소 그룹이며 공원 보장 아님. 원본 category_name은 별도 필드. provider가 완전한 공원 ontology를 보장한다는 주장 제외. Spring AI 2.0.0 원본 소스에서 Java Tool 반환값의 모델 전달·직렬화 확인. 버전별 신규 제한 기능이나 streaming 필터를 장소 의미 검증에 대신 사용하지 않음. 출처 링크는 조사 문서에 보존.

| 결정 frontier | 추천 자동 채택 | 제한 |
|---|---|---|
| 검색과 최종 guard | 공원 자격 공유·선택 전 필터 | 일반 관광지·TourAPI 분류 추론 확대 제외 |
| 메뉴 실패 | 기존 슬롯별 복구 재사용 | 구조를 해석할 수 없는 오류는 응답 단위 처리 유지 |
| 늦은 보충 | 먼저 공개 생성 fixture 재현, 마지막 검증 추가 후 callback 이전 | 최종 출처 검사 약화 금지 |
| 편집 보존 | 대상 밖 날짜 예약 유지, 기존 슬롯 provider 재검증 제외 | 저장 슬롯 검증과 새 공급자 출처 검증 구분 |
| 비용·다음 단계 | 무료 protocol fixture, 3·4단계 조건부 | 실제 API 호출·main sync 승인 자동 확대 없음 |

## 4. TDD 증거

검증 지점은 사전에 정한 공개 `PlanTourismTool.findPlaceWithRoute`, `PlanService.makePlanByAi`, `PlanService.makeEditPlanByAi`. 새 생성·편집 fixture는 실제 OpenAiClient·Spring AI tool loop·handler·mapper·resolver·normalizer·completer 사용. 모델 응답은 로컬 OpenAI HTTP protocol fixture, Kakao·영양 API는 로컬 HTTP 서버, Kor2는 외부 HTTP adapter의 scripted response. repository 경계만 mock. 운영 모델 생성 품질과 구분.

| slice | 의도한 RED | GREEN |
|---|---|---|
| S1 공원 검색 | kakao:park 기대, kakao:lake 반환 | 공원만 선택·등록, 호수 미등록 |
| S10 늦은 보충 | 원본 위도 181 거부 기대, 그대로 성공 | 빈 값·181·NaN 좌표 거부, 유효 원본 식사 보존 |
| S5 국소 메뉴 복구 | callback 전체 교정이 재선택 응답으로 실패 | 잘못된 메뉴만 변경, 뒤쪽 정상 관광지 ID 보존 |
| 리뷰 후 편집 보존 | 유지 3일차 식당02/불고기를 1일차 아침에 삽입 | 대상 밖 장소·메뉴 예약 유지, 유지 3일차 선택 보존 |

fixture 작성 중 enum·시간 직렬화와 잘못된 예상값 오류는 RED 증거에서 제외. 표는 실제 사용자 결과 반례가 재현된 실패만 포함.

추가 회귀: 분류 근거 없는 공원 제외·일반 호수 관광 유지·이미 사용한 공원 제외, 등록 점심의 원본 필드 확정, 늦은 식사 출처 불일치·중복 메뉴·잘못된 유형 삽입 제외, 메뉴/미등록 ID/다른 유형의 두 번 재선택 후 거부, JSON 파싱 실패의 기존 두 번 응답 처리 유지.

유효 재선택 fixture의 물리 OpenAI 왕복 4회, 유효하지 않은 재선택 두 번 fixture 6회, 부분 편집 fixture 3회. 이는 각각 고정된 Tool 대화의 결과이며 운영 호출 상한 아님. 슬롯 재선택 2회와 내부 Tool loop·SDK retry·callback correction 예산 구분.

## 5. 검증과 리뷰

최종 수치와 검토 결과: `p2-selection-recovery-stage2-verification.json`. RED/GREEN 로그·계약 XML·protocol fixture·미커밋 patch: `p2-selection-recovery-stage2-evidence/`. 전체 로그와 XML은 가상 사용자·로컬 공급자 데이터이며 실제 응답 원본 아님.

- 편집 보존 추가 전 전체 test: 928건, 실패·오류·skip 0
- 편집 수정 후 생성·편집·공원 targeted test: 79건, 실패·오류·skip 0
- 최종 전체 회귀: 929건, 실패·오류·skip 0. BUILD SUCCESSFUL
- review1: 9.3/10, Critical 0·Important 0. 신규 코드 체인·인자 서식 수정
- review2: 8.7/10, Critical 0·Important 1. 부분 편집 예약 누락 재현 및 수정
- 최종 인자 줄 배치 수정 후 compileTestJava 통과, 동작 변경 없음
- review3: 9.3/10, Critical 0·Important 0·Minor 0. 독립 확인 후 최종 gate 통과

위 무료 검증의 `test` task는 external 태그 제외. 해당 검증에는 실제 OpenAI·TourAPI·식약처 호출 없음. 이후 사용자 승인한 실제 API 평가 결과는 아래 §6에 별도 기록. 커밋·PR·dev 전달과 품질 채택 상태 구분.

## 6. 실제 API 품질 평가와 Grafana (2026-10-11)

사용자의 API·Grafana 측정 지시에 따라 생성 12회 묶음 1회 실행. 추가 유료 묶음·실제 편집·STOMP 0회. 기존 `TravelLlmQualityBaselineTest`의 C1 서울·C2 부산·C3 강릉·C4 경주 각 3회 재사용. `./gradlew externalTest --tests com.planb.integration.domain.travel.TravelLlmQualityBaselineTest --console=plain`, `P2_GENERATION_ONLY=true`. JUnit 실행 12건·실패/오류/skip 0과 결과 JSONL의 PASS 12건을 별도 확인.

모델 `gpt-5.6-terra`, reasoning `none`, 기존 입력·seed·Tool 구성 유지. MockMvc를 통한 실제 생성 경로와 실제 OpenAI·TourAPI·Kakao·영양 API 사용. DB/Redis만 신규 Testcontainers 사용. 묶음 시작 Redis 초기화, 지역·회차 간 캐시 재사용. 각 지역 첫 회차의 전 API 미적중 보장 없음. 부하 테스트·운영 동시성 측정과 구분.

- 직전 비교: #177 최종 메뉴 수정, run `2026-10-11T03-03-33.798400Z`
- 이번 실행: #179 미커밋 변경, run `2026-10-11T11-45-59.495191Z`
- R0: 누적 품질 gate 기준. 직전 #177: 개별 변경 비교 기준
- 코드: 기준 dev `f236f953` 위 미커밋 patch, 실행 전후 운영 소스 hash 동일. 실행 manifest에 전체 운영 소스·기존 평가 러너 hash 보존
- 같은 case/repeat/seed이며 다른 시각·모델/공급자 응답. 동일 결과를 재생한 인과 실험이나 통계적 유의성 증명 아님

### 6.1 품질과 결과 필드

| 항목 | #177 최종 메뉴 수정 | #179 후속 2단계 | 해석 |
|---|---:|---:|---|
| 생성·기존 구조 검사 | 11/12 | 12/12 | 전체 12시도 기준, 완전한 여행 품질 지표 아님 |
| 강릉 C3 | 2/3 | 3/3 | 이번 세 응답 모두 통과, 과거 호수 실패의 동일 후보 재생 아님 |
| 지정 장소 포함 | 11/12 | 12/12 | 성공 응답만 포함 계수, 실패는 포함 성공으로 세지 않음 |
| 필수 식사 존재 | 44/44 | 48/48 | 성공 11→12회, 평가 22→24일. 첫 아침·마지막 저녁 면제 |
| 복약 구현 규칙 일치일 | 22/22 | 24/24 | 장소 종료 뒤 이동까지 포함한 기대값. 등록 식후 간격 준수와 별개 |
| 필수 필드 오류 슬롯 | 0/144 | 0/156 | 원본 응답 필드 검사, 실제 장소·영업 정확성과 구분 |
| 경로 숫자 누락·음수 슬롯 | 0/144 | 0/156 | 외부 경로값의 현실 타당성 재검증 아님 |
| 날짜 간 장소 중복 | 0 | 0 | 성공 응답의 기존 중복 계수 |
| 정규화 메뉴 문자열 중복 | 0 | 0 | 후처리 문자열 proxy, strict 출처와 별개 |
| 알레르기 키워드 proxy | 0 | 0 | 실제 재료 안전성 증명 아님 |
| 기존 메뉴 출처 proxy | 66/66 | 72/72 | 별도 상세조회·양방향 부분 문자열 검사, strict 원본 일치율 아님 |
| 영양 3필드 숫자 채움 | 30/66 (45.5%) | 33/72 (45.8%) | 양쪽 carbohydrate proxy와 수가 동일, 성분 정확성·HIGH 분량 아님 |
| 필수 식사 슬롯 ±30분 | 41/44 (93.2%) | 42/48 (87.5%) | 정시 비율 하락 관측, 태그 불일치 양쪽 0. 이동 제약 우선의 늦은 무태그 식사와 하드 위반 구분 |
| PARK_WALK 반환 슬롯 | 0 | 0 | 실제 공원 선택의 개선·원본 분류 정답률 미입증 |

원본 결과 비율의 분모는 성공 응답에 한정. 기존 실패 1회는 필드·식사·영양·복약 분모에서 제외. 전체 API 성공 수와 성공 응답 안의 필드 비율을 혼용하지 않음. `saved=false`는 생성 후 확정 대기 상태이며 저장 실패로 판정하지 않음.

### 6.2 품질 gate와 보조 지표

R0 누적 주 gate 4개(통과·지정 장소 ≥11/12, 알레르기 키워드 0, 날짜 간 장소 중복 0) 모두 충족. 측정한 감시 기준 5개(영양 하락 ≤10pp, 메뉴 proxy 하락 ≤10pp, 카페 수 ≥R0의 50%, 관광지당 태그 하락 ≤30%, 이동시간 중앙값 증가 ≤20%) 충족. HIGH·영업시간은 `NOT_EVALUABLE`이며 통과 항목에 포함하지 않음. #177 이동시간 예외를 이번 이슈로 승계하지 않음.

| 지표 | #177 | #179 | 해석 |
|---|---:|---:|---|
| 슬롯 재선택 단계 | 4 | 3 | 전체 각 12시도, 모델 왕복·SDK retry와 별도 |
| 앱 교정 시도 | 4 | 0 | 이번 응답 단위 교정 0 관측, future 상한·전체 retry 0 주장 아님 |
| 물리 모델 요청 관측 | 73 | 69 | 전체 12시도, Java 재선택 3회와 동일 단위 아님 |
| 성공 응답 지연 중앙값 | 20.399초 | 23.517초 | 약 15.3% 증가. 지연 감소로 포장하지 않음, 채택 gate 아님 |
| 성공 응답 입출력 token 중앙값 | 51,497.0 | 54,437.5 | cache-read를 별도 합산하지 않은 input+output. 실제 청구 비용 아님 |
| 일정 이동시간 합계 중앙값 | 333분 | 291.5분 | R0 271분 대비 7.6%, 응답 지연과 별개 |

이번 강릉 3회는 각각 재선택 단계 1회 뒤 성공. 재선택 후보·입출력 상세 trace가 원본 응답에 없으므로 어떤 오류가 복구됐는지, 최초 정상 관광지 ID가 보존됐는지 실제 호출에서 독립 증명하지 않음. 공원 자격·정상 선택 보존·늦은 보충·부분 편집 예약의 증거는 §4 결정적 공개 seam 테스트로 한정. 이전 실패 후보를 실제로 다시 선택시킨 실험과 구분.

판정: **측정한 회귀 gate 충족·국소 복구 경로의 성공 관측. 공원 실제 선택 개선·전체 AI 품질 개선 완료는 판정 보류.** 필수 식사 정시 비율의 하락과 영양 미채움 39/72슬롯도 보존. 추가 유료 묶음 자동 실행 없음. 3·4단계 착수·dev 전달·운영 채택은 별도 결정.

### 6.3 Grafana와 영구 증거

[Grafana 캡처 화면](http://localhost:3000/d/p2-selection-stage2/5a24d2d?from=2026-10-11T03%3A02%3A33.798400%2B00%3A00&to=2026-10-11T11%3A55%3A56.761723%2B00%3A00&timezone=browser&orgId=1) — 대시보드 UID `p2-selection-stage2`, **Part 2 후속 2단계 #179**. Part 1 5C·기존 travel-load 부하 화면과 구분.

평가 JSONL과 응답 raw를 후처리한 gauge를 Prometheus로 가져온 대시보드. 실시간 앱 scrape·k6·운영 p95가 아님. 23개 숫자 패널의 두 판본 값을 원본 결과와 대조하고 Grafana datasource proxy로도 일치 확인. 측정·문서 독립 리뷰 8→8.9→9.3/10, 최종 Critical·Important·Minor 0. 분모·datasource·기존 값 보존·보관 사본 지적 해소. 저장된 dashboard UID·HTTP 페이지 접근 확인. 앱 브라우저에서 제목·설명·앞쪽 통과/지정 장소 패널의 11→12 값 표시 확인. 나머지 패널의 개별 스크롤 확인은 미실시. 기존 품질 화면 17개 패널 값은 이전 기록과 동일, 전체 TSDB 내용의 byte 비교는 미실시.

localhost 링크는 로컬 Grafana·Prometheus 기동에 의존. 관측용 Prometheus 보존 30일이며 영구 증거는 아래 파일로 보존. 실제 응답·결과 JSONL은 synthetic 계정의 이번 평가자료. API 키·인증 헤더·원본 프로세스 전체 로그는 보관 자료에서 제외.

- 집계: `p2-selection-recovery-stage2-actual-quality-20261011.json`
- 실행 manifest·JSONL 12건·raw 12건·직전 비교 JSONL: `evidence/p2-selection-recovery-stage2-20261011T114549Z/` (raw·JSONL은 Git 제외, 로컬 원본과 보관 사본만 유지)
- 응답 필드 상세 감사: `evidence/p2-selection-recovery-stage2-20261011T114549Z/supplemental-raw-quality.json`
- 대시보드·영구 gauge·패널 검증: `evidence/p2-selection-recovery-stage2-20261011T114549Z/grafana-quality-comparison.json`, `quality-comparison.openmetrics`, `grafana-query-verification.json`
- 기존 화면 값 보존 확인: `evidence/p2-selection-recovery-stage2-20261011T114549Z/previous-observation-preservation.json`
- 코드/TDD 증거: `p2-selection-recovery-stage2-evidence/`, 기존 source hash·patch 유지
- 프로젝트 원본과 지정 보관 디렉터리의 동일 사본 유지. 계획 문서 스테이징 금지 유지

## 7. 남은 판단

실제 API 평가 완료와 전체 품질 개선 완료 구분. 공원 원본 분류·strict 메뉴 출처·정상 슬롯 보존의 live trace, 실제 편집·STOMP·HIGH 분량·영업시간·실제 재료 안전성 미검증. 추가 실제 API 묶음·새 구조·main sync 자동 실행 제외. 소스는 dev PR로 전달, main sync·운영 배포 없음.


## 8. 식사·복약 후속 진단에 따른 해석 보완 (2026-10-12)

새 API 요청 없이 기존 raw 재계수. 식사 창 밖6건은 최종 순서·이동·체류·직전 식사를 고정한 조건에서 정시화 불가. 아침 이동과 전체 여유 압축을 가정하면3건 가능이며 새 정책은 미채택. 새 골격 도입 근거 부족. 기존 API결과/수치/코드 patch/관측Grafana는 변경하지 않음.

복약22/22·24/24는 현재 구현 규칙의 재현 일치값. 기존 오프라인 검사도 `moveOutOfPlaceSlots`를 따라 장소 종료 시각으로 expected를 이동했으므로 설정된 식후 30분 준수의 독립 증명이 아님. 등록간격 기준으로는 이전10/22·이후9/24 슬롯이 이탈(이후 준수 15/24). 전체 복약 구간과 장소 구간의 교차는 각1건. 해당 표본은 날짜당 합쳐진 복약슬롯1개이며 동행인별 임상 적정성을 검증하지 않음. 기존 Grafana의 “복약 deterministic 계약”100%를 등록간격100%로 해석하지 않음; 새 지표import는 이번 작업에서 미실시.

Claude 원본 진단과 Codex 오프라인 재계수/실험 clone 무료 29건 확인은 `p2-meal-time-window-diagnosis-result.md`, `p2-meal-time-diagnosis-evidence/codex-handoff-crosscheck.json`참조. 새 시간정책·복약우선순위는 별도 사용자 결정, 운영코드 미수정.
