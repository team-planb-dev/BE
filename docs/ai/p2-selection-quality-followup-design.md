# Part 2 선택 품질 후속 2·3·4단계 설계

작성일: 2026-10-11 · 개정: 2026-10-12 · 상태: 2단계 #179 로컬 구현·무료 검증·실제 생성 평가·Grafana 완료, 전체 품질 판정·dev 전달 전. 3·4단계 구조 도입 보류. Claude의 3단계 식사 진단 인수 완료, 복약 시간 정책 결정과 Codex 후속 진단 대기

기준 코드: dev commit `f236f953f6ca3dd55cbb67f1e20142c0fe4ec280`(PR #178). 읽은 작업 트리: `/private/tmp/planb-selection-contract-177`, HEAD `bc369d03b9217a1a565e62dbbe15e91c12486d48`, tree `8624362e2467e12a80c821ef623e92df367e579e`. GitHub commit API로 dev tree도 `8624362e2467e12a80c821ef623e92df367e579e`임을 확인. 구현 착수 시 최신 dev를 다시 고정. main `e2b5a05`는 P2-4A 운영 기준이며 이번 문서 작성으로 변경하지 않음.

## 1. 요약

목표는 생성 성공 1건 증가보다 **장소·메뉴의 사실 정확성, 요청 반영, 실패와 무관한 정상 결과 보존**의 개선. 생성 성공률은 회귀 감시 지표로 유지. 기존 필수 식사·복약 계약을 유지한 결과는 신규 품질 개선과 구분.

진행 순서는 2) 실패 종류별 복구와 정상 슬롯 보존, 3) Java 필수 일정 골격 필요성 판단, 4) AI 결정 의존 관계 분리 필요성 판단. 2026-10-12 갱신: 2단계 구현/실제 생성 평가 이후, 3단계의 기존 식사·시간표 계약 진단을 Claude에 위임하고 Codex가 영양·증거·4단계 판단을 이어받음. 3·4단계의 설계·판단은 수행하되 도입은 실패 증거와 품질상 이득을 확인한 경우에만 진행. 이 번호는 Part 1 Phase 2·3·4 및 P2-5A·P2-5B와 별개인 후속 순서.

## 2. 목표와 제외 범위

| 목표 | 제외 범위 |
|---|---|
| 공원 슬롯에 검증된 공원 후보만 선택하도록 검색·검증 계약 정렬 | 호수를 공원으로 승인하거나 PARK_WALK를 ATTRACTION으로 자동 변경 |
| 복구 가능한 슬롯 오류와 응답 구조 오류의 처리 분리 | 검증 삭제, 무제한 재시도, 모델·라이브러리 교체 |
| 실패 슬롯 밖 정상 선택값 보존의 결정적 증거 확보 | 속도 감소를 세부 품질 개선으로 환산 |
| 3·4단계의 도입·보류 근거와 실험 gate 구체화 | 기존 식사·복약 보충과 중복되는 새 프레임워크 |
| 요청 컨텍스트 격리·최종 검증·원자적 저장 유지 | STOMP 응답 계약 변경, schema migration, main sync·배포 |

실제 영업시간, 메뉴의 실제 재료, HIGH 판정의 1회분량은 관측 또는 제품 기준이 없으면 `NOT_EVALUABLE`. 알레르기 키워드 검사는 안전성의 proxy이며 실제 성분 위반 확정과 구분. 총 이동시간 감시 기준은 master §6.3 유지. #177의 이동시간 예외는 다음 이슈로 자동 승계하지 않음.

## 3. 배경과 확인된 코드

| 사실 | 기준 코드 근거 | 해석 제한 |
|---|---|---|
| #177 수정 후 생성 11/12, 경주 3/3·괄호 메뉴 원문 3/3, 잘린 메뉴 0건 | `p2-selection-menu-postfix-actual-quality-20261011.json` | 작은 시나리오 표본, 전체 여행 품질 일반화 불가 |
| 남은 강릉 1건: AT4 호수를 PARK_WALK로 선택해 거부 | `evidence/p2-selection-menu-postfix-20261011T030316Z/place-type-diagnosis.json` | 최초 선택·각 재선택 후보 미보존으로 모델 의도·공원 부족 단정 불가 |
| 초기 생성은 일수·관광지 보충 가능성·음식점 메뉴 검증 callback 사용 | `TravelRecommendHandler.java:110–134,278–365` | 최종 장소 유형 검증 전체가 초기 callback에 있는 것은 아님 |
| 초기 검증 실패는 전체 응답 correction 1회 후 실패 가능 | `OpenAiClient.java:164–239,270–308,386–416` | 정상 값 유지는 프롬프트 지시이며 Java 강제 보존 증거 없음 |
| 모든 정상 슬롯을 먼저 예약하고 실패 슬롯만 재선택 | `PlanService.java:1260–1343,1464–1515` | 초기 callback에서 종료하면 이 복구 경로에 도달하지 않음 |
| 재선택 결과를 반환받는 경우 슬롯당 최대 2회 | `PlanService.java:1487–1515` | 재선택 호출 자체가 예외를 던지면 다음 시도·fallback 이전에 전파 가능 |
| PARK_WALK는 AT4/12와 공원 categoryName 모두 필요 | `PlanPlaceResolver.java:404–407`, `PlanTourismTool.java:199–242` | Tool은 AT4 그룹만 선택하여 호수도 반환 가능. TourAPI categoryName은 현재 null |
| `select`가 원래 슬롯 유형·시각·체류시간·태그·복약 유지 | `PlanPlaceResolver.java:243–266` | 후속 경로·시간표 normalization은 별도의 파생값 변경 가능 |
| Java 식사·관광 보충과 복약 배치가 이미 존재 | `PlanService.java:1176–1259,770–835`, `MissingSlotCompleter`, `ScheduleNormalizer` | AI 이전의 완전한 골격 생성과 동일하지는 않음 |

경로·행 번호는 위 기준 코드 기준. 조사 자료: [공식 근거와 코드 조사](p2-selection-quality-followup-research.md).

## 4. 제안 구조

### 4.1 후속 2단계 — 기존 복구 module 보강

외부 interface는 `PlanService.makePlanByAi(TravelPlanContext)` 유지. 테스트는 이 seam의 반환 결과·실패 계약을 중심으로 수행. 실제 API adapter는 로컬 stub으로 교체하고, 정책·매퍼·컨텍스트·normalization implementation은 실제 코드 사용. 새 generic recovery framework, 전역 상태, DB 저장 전 임시 entity 도입 제외.

첫 구현 후보는 **공원 후보 적합성의 검색·최종 검증 정렬**. Tool의 입력 `courseType`에 맞는 후보 자격을 공통 Java 규칙으로 확인하고, 잘못된 AT4 후보를 선택 가능한 공원 근거로 기록하지 않도록 설계. 기존 최종 guard는 유지. 두 호출 위치에서 실제로 사용하는 경우에만 자격 판정을 domain/travel의 작은 policy module로 추출. 새 정책 이름·Tool constructor 변경은 호출자 조사 후 확정하며 Facade는 Service DI만 유지.

다음 후보는 실패의 기계적 분류. 분류 위치는 검증을 소유하는 module이며 자연어 오류 문장·메뉴의 쉼표를 다시 파싱해 종류를 추측하는 방식 제외. 최소 분류는 후보 없음, 유형 불일치, 메뉴 상세 미확인/원본 불일치, 중복, 구조 오류, 경로 오류, upstream 오류. 공개 REST/STOMP 예외 코드는 기존 계약 유지. 편집 fallback의 `verifyExisting`은 동일 저장 슬롯의 구조·시간·중복·메뉴 존재 검사이며 이번 요청의 provider 출처·좌표 재확인이 아님. 이를 source 검증 성공으로 계수하지 않음. 내부 분류 추가가 필요할 경우 기존 `Validation`·검증 결과에 제한하며 새 전역 예외 계층 추가 제외.

초기 callback의 슬롯 오류를 국소 복구로 넘기는 변경은 **검증 위치 이전**이며 검증 삭제가 아님. 타입과 날짜 구조가 사용 가능한 응답만 대상으로 지정. 최종 메뉴 출처 검사, 필수 일정, 중복 검증은 반환·저장 전 완료 계약. 현재 `finishPlan.refillMissingMeals`의 삽입 뒤 resolver 재검증은 명시적으로 보이지 않으므로 기존 보충이 동일 출처·필드 계약을 충족하는지 fixture로 먼저 확인. 누락이 재현되면 최종 후보/메뉴 확정 경계에서 최소 검증 추가, 반복 LLM 재선택 루프는 추가하지 않음. parser 오류·일수/날짜 식별자 오류·refusal·upstream 실패는 슬롯을 안정적으로 식별할 수 없으므로 현행 응답 단위 처리 유지. 초기 callback의 검증 이전 착수 gate는 늦은 식사 보충까지 출처·유형·중복·좌표 계약을 공개 생성 seam에서 입증하는 것. 먼저 기존 menu 실패가 초기 callback에서 막히는 fixture와 늦은 보충 fixture를 재현하고, 그 다음 이 이전이 필요한지 결정.

| 실패 종류 | 권장 처리 | 금지 또는 중단 조건 |
|---|---|---|
| PARK_WALK 유형 불일치 | 공원 근거가 확인된 후보만 현재 슬롯의 선택 대상으로 제한 | 공원 부족을 호수 승인·유형 전환으로 은폐 |
| 메뉴 상세 미확인/원본 불일치 | 같은 음식점 상세의 실제 메뉴 또는 적합한 다른 음식점 선택, #177 계약 재사용 | 부분 문자열·임의 메뉴·영양 표준명으로 출처 승인 |
| 후보 없음·중복 | 정상 슬롯 예약 유지, 현재 실패 슬롯에 유효한 미사용 후보만 제공 | 뒤쪽 정상 슬롯의 후보 빼앗기, 전 일정 재선택 |
| 날짜/일수/JSON 구조 오류 | 안정적 slot identity가 없으면 기존 parse/correction 처리 | 잘못된 day를 임의 매칭해 국소 수정 |
| 실제 후보·상세·경로 부족 | 확인된 정책으로 보충 가능하면 Java 보충, 불가능하면 실패 유지 | 성공률을 위해 필수 슬롯 삭제·이동시간 생성 |
| 시각·체류시간 오류 | 후보 교체가 아닌 기존 정규화·필드 검증으로 처리 | `select`가 원래 시각을 보존하므로 같은 장소 재선택 반복으로 해결 기대 금지 |
| HTTP·parse·예상 밖 예외 | 현재 예외 계약 유지, 관측 가능한 원인 기록 | 모든 예외를 catch해서 재시도/성공 fallback으로 전환 |

공원 적합 후보가 있다는 사실과 후보를 아직 발견하지 못했다는 상태를 구분. 후보 0건은 “이 검색에서 적합 후보 없음”이며 지역 전체에 공원이 없다는 증거가 아님. 검색 범위 확장은 같은 여행 시 내부만 허용하고, 기존 검색 모듈의 실제 지원·호출 상한을 확인한 뒤 별도 slice로 진행. 신규 카테고리 검색 endpoint나 지도 반경 API는 자동 도입하지 않음.

### 4.2 후속 3단계 — 필수 일정 골격 필요성 판단

현재 판단: **새 골격 module 도입 보류, 기존 Java 보충의 계약 검증 우선**. 기존 `MealSlotPolicy`, `TouristPlaceCountPolicy`, `MissingSlotCompleter`, `ScheduleNormalizer`가 이미 처리하는 계약을 다시 정의하지 않음.

도입 검토 조건은 필수 슬롯 누락이 Java 보충 후에도 모델 구조/시각 선택 때문에 반복되며, 후보·상세·경로가 유효한 fixture에서 그 원인이 재현되는 경우. 이때 기존 policy 결과로 day/date·필수 식사·복약의 최소 틀을 산출하고 AI가 틀 안의 장소·메뉴만 선택하는 단일 비교 실험 검토. 복약의 식전·식후 시각은 최종 식사 시각 확정 이후 의존성이 있으므로 AI 전 고정 배치 금지.

후보 부족·외부 실패·미정 제품 정책만이 원인이면 골격 도입 기각. 골격이 기존 보충보다 요청 반영을 떨어뜨리거나 정상 일정의 유형·시각을 불필요하게 바꾸면 미채택. 관측 증거가 없으면 “판단 완료·도입 보류”로 기록 가능하며 구현 완료와 구분.

### 4.3 후속 4단계 — AI 결정 의존 관계 분리 필요성 판단

먼저 실제 의존 그래프 확보. 구조상의 기본 순서는 검색된 후보·원본 상세, 장소/메뉴 선택, Java 출처·유형 검증, 인접 장소 경로·시간표, 필수 식사 재보정·영양 보강, 최종 식사 기반 복약·태그, write 저장. 현재 Tool 호출과 AI 판단은 한 loop 안에서 섞여 있어 이 순서를 그대로 별도 호출 목록으로 착각하지 않음.

현재 판단: **날짜별 fan-out 자동 도입 보류**. 재시도에서 정상 슬롯 훼손 또는 서로 다른 판단 책임의 혼선이 확인되고, 작은 선택 interface로 분리한 fixture가 이를 줄이는 경우에만 실험. 응답 시간만 줄었다는 결과로 채택하지 않음.

첫 비교 후보는 모든 날짜 병렬화가 아니라 기존 실패 슬롯의 작은 선택 계약. 날짜별 분리를 검토하는 경우 지정 장소 우선 배정, 날짜 간 중복 예약, 후보 고갈, 건강 문맥 최소화, 병합 순서·취소·실패 범위를 먼저 정의. `PlaceCandidateContext`는 요청별 concurrent collection 상태이며 ThreadLocal이 아님. 후보 초기화·검색·예약을 worker 사이에서 동시에 수행할 계약은 현재 없음. `NutritionEvaluationCollector`와 `AiCorrectionTracker`의 ThreadLocal 상태는 새 worker로 자동 전파되지 않으므로 명시 전달 없이 Tool 흐름 이전 금지. 하루 선택의 앞 장소가 다음 경로 입력인 경우 독립성 없음. 새 async framework, `@Async`, Reactor 전면 전환은 이번 범위 제외.

## 5. 요청 생명주기와 실행 순서

1. 새 구현 Issue 착수 시 최신 dev 확인·기준 commit 고정. master 계획 두 파일은 미추적 유지. P2-4B/C 미채택 코드 누적 제외.
2. 기존 관측 자료에서 initial/correction/reselect/finish 실패 위치 구분. 부족한 trace는 UNKNOWN 표기 후 결정적 fixture로 구분.
3. 실패 원인 1개와 확인 seam 1개를 선택하고 RED 재현. 필요한 최소 정책·Tool 수정 후 GREEN. 다음 원인은 별도 slice.
4. 정상 슬롯 선택값·재시도·후처리·원자적 저장 회귀 검증. review-loop 최소 2회, 최종 9/10 이상 및 Critical/Important 0건.
5. 2단계 결과를 근거로 3단계 기존 골격 검증, 4단계 의존 분리 필요성 판정. 이전 단계가 충분하면 도입 없이 조사 결과 보존.
6. 실제 API 실행 범위·비용·품질 판정 항목을 사용자 승인 후 수행. 기존 12회 승인은 소진된 과거 승인으로 처리.
7. 코드 채택 시 별도 Issue·branch·PR로 dev에 전달. main sync는 사용자 별도 지시 전 금지. 문서와 데이터 사본 동일성 검증.

## 6. 데이터·품질 계약과 TDD seam

### 6.1 정상 결과 보존의 관측 지점

두 결과를 구분. 슬롯 선택 복구 전후에는 정상 슬롯의 day/date·순서·candidate identity·courseType·메뉴·체류시간·태그·복약값 보존. 최종 API 응답은 인접 장소 변경으로 `travelMinutes`, start/end 시각, 최종 영양 수치·정책 태그가 달라질 수 있으므로 전체 객체 byte equality를 요구하지 않음. 허용 변화는 해당 경로·시간표·영양 정책에서 계산된 값으로 별도 단언. unrelated day 변경을 무조건 정상으로 승인하지 않고 실제 연결 경로의 영향 여부 검증.

| seam | 관측할 사용자 계약 | 테스트 수단 |
|---|---|---|
| `PlanService.makePlanByAi` | 공원 자격, 메뉴 원문, 정상 슬롯 보존, 일수·필수 일정·최종 실패 | 실제 정책·컨텍스트, 외부 OpenAI/HTTP stub |
| `TravelRecommendHandler.createPlanByAi` | 초기 구조 검증 유지와 복구 가능한 슬롯 오류의 전달 | 실제 매퍼·callback, 구조화 응답 stub |
| `PlanService.makeEditPlanByAi` | 같은 공통 복구 변경이 편집 요청 밖 선택을 손상하지 않음 | 기존 edit contract와 부분 재구성 회귀 |
| 생성·조회 공개 interface | 성공 결과 조회 가능, 실패 시 부분 일정 없음 | 기존 IntegrationTest의 공개 생성·조회 호출, 직접 DB row 조회 대신 interface 확인 |

위 seam은 기존 사용자 승인한 생성 결과·잘못된 ID 거부·편집 유지 및 grilling 추천 자동 채택 지시에 따른 문서 확정안. 내부 private method를 반사 호출하거나 단순 mock 호출 횟수만으로 정상 결과 보존을 증명하지 않음. 호출 예산은 비용 계약이므로 결과 검증과 함께 외부 adapter의 실제 요청 ledger에서 별도 관측.

### 6.2 최소 RED/GREEN slice

| 순서 | 입력/반례 | 통과 계약 |
|---|---|---|
| S1 | 같은 AT4의 공원·호수·null category 후보 | PARK_WALK에 공원만 선택 가능, lake 승인 없음 |
| S2 | 앞 슬롯 오류, 뒤쪽 정상 장소·메뉴 | 복구가 정상 후보를 빼앗지 않으며 정상 선택값 유지 |
| S3 | 재선택이 유형·시각·체류시간까지 바꾼 응답 | 원래 slot 의미 보존, 필요한 파생 경로만 재계산 |
| S4 | 후보 없음·모든 후보 사용·공원 근거 부족 | bounded failure, 필수 슬롯/guard 삭제 없음 |
| S5 | 초기 메뉴 오류와 정상 날짜가 섞인 구조화 응답 | callback 경로 재현 후 필요시 슬롯 오류만 국소 복구, 최종 출처 검사 유지 |
| S6 | JSON/일수 오류, refusal, 호출 자체 예외 | slot 식별 불가능 시 응답 단위 실패, 예외 계약 유지 |
| S7 | 재선택 2회 모두 invalid, edit 원본 없음/있음 | 생성은 실패, 편집은 동일 저장 슬롯의 구조·시간·중복·메뉴 존재 검증 후 현행 fallback. 최신 후보 출처 재확인과 구분 |
| S8 | 여러 실패 슬롯·두 요청 동시 실행 | 예약 상태·메뉴/영양 교차 오염 없음, 예산 증폭 설명 가능 |
| S9 | 기존 Java 식사·복약 보충과 중복 응답 | 최종 정책 충족, 동일 보충 반복의 중복 생성 없음 |
| S10 | 시간 정규화 이후 늦은 필수 식사 보충, 유효/부적합 후보 | 공개 생성 결과에서 출처·유형·중복·좌표 계약 동등성 확인. 미입증 상태에서는 초기 callback 검증 이전 미착수 |

S1도 기존 테스트가 통과하면 RED를 억지로 만들지 않음. 검색 Tool과 최종 정책이 다른 결과를 만드는 실제 seam의 반례부터 선택. 한 테스트·최소 구현씩 진행. 이번 문서 작업에서는 production/test 변경과 RED/GREEN 실행 없음.

`property-based-testing`은 정상 슬롯 수·실패 위치·메뉴 구분자·중복 후보 조합의 invariant 설계에 사용. 기존 JUnit parameterized/dynamic test로 유한 조합을 먼저 검증하고, 이를 전체 입력 공간 증명으로 표현하지 않음. jqwik은 현재 미설치이며 새 테스트 의존성 추가는 별도 결정. seed·생성 개수·제약·최소 반례를 기록하고 구현과 같은 알고리즘으로 expected 값을 재계산하는 테스트 제외.

### 6.3 품질 평가표

`llm-evaluation`의 평가 유형 중 도메인 계약 단언·동일 사례 전후 비교 적용. BLEU/ROUGE·문장 유사도를 일정 정확성의 대체값으로 사용하지 않음. LLM judge의 점수는 보조 의견이며 provider 사실·영양 안전성의 정답으로 취급하지 않음.

| 항목 | 분모·관측 자료 | 판정 |
|---|---|---|
| 장소 유형 정확성 | 근거를 가진 장소 슬롯 전체, source category와 요청 courseType | fixture 100%, 실제 관측 위반 0건. 근거 없는 슬롯 UNKNOWN 별도 계수 |
| 메뉴 출처·원문 | TourAPI 39 음식점 슬롯과 동일 응답의 verified detail | 원문 일치·다른 식당 혼입 0건. FD6·상세 미보존은 별도 범위 |
| 정상 슬롯 선택값 보존 | 실패 주입 전 검증된 정상 슬롯 | fixture 100%. 초기 correction 전값이 없으면 실제 보존률 NOT_EVALUABLE |
| 요청 반영 | 지정 장소 ID·요청 유형, 해당 case 전체 | 정답 identity 및 요청 종류 대조, 이름 substring만으로 정답 판정 금지 |
| 복구 결과 정확성 | 종류별 복구 대상 슬롯 및 후보 존재/없음 fixture | 성공은 최종 검증 필요, 후보 없음은 올바른 거부도 정답 |
| 일정 완성도 | 모든 요청의 최종 성공/실패와 성공 응답의 날짜별 필수 슬롯 | 실패를 분모에서 몰래 제외하지 않고 request-level 성공과 slot completeness 함께 보고 |
| 건강·영업시간·재료 | 1회분량 정책·시간대·성분 원본이 있는 관측만 | 현재 증거 없는 항목 NOT_EVALUABLE, proxy와 분리 |

실제 API 평가 전 모델/설정, case 입력, 기준/변경 commit·미커밋 diff, 요청·슬롯별 원본 출처, 캐시 조건, 관측 시각, 비용·호출 예산 고정. 기존 4 case × 3회의 12회는 master의 회귀 감시 형식이며 새로운 통계적 신뢰도 또는 모든 품질 항목의 정답 근거가 아님. 개선 대상 유형 오류/메뉴/보존은 그에 맞는 fixture와 기록이 별도로 필요. 성공 수 증가만으로 추가 품질 gate를 대체하지 않음.

## 7. 일관성·재시도·파급효과

슬롯당 최대 2회는 재선택 method invocation 기준. 내부 parse 재호출, 초기 전체 correction, SDK 네트워크 재시도, Tool loop의 LLM 왕복은 별도로 집계. 실패 슬롯 K개에서 상위 재선택 호출은 최대 2K 가능하며 요청 전체 2회라고 표현하지 않음. 재선택 내부 예외는 현행 즉시 전파이며 자동 outer 재시도 추가 없음. 예산 확대 또는 새 요청 전체 제한이 필요하면 별도 비용 정책으로 결정.

가장 큰 파급효과는 초기 검증 결과를 최종 복구로 넘길 때 잘못된 메뉴가 저장될 위험, 공원 자격 규칙 변경이 ATTRACTION/MUST_HAVE를 좁히는 위험, 부분 실패 이후 재계산이 정상 선택값까지 덮는 위험. 회귀 범위는 생성·재선택·식사 보충·편집·날짜 재구성의 공통 resolver와 handler. REST/STOMP 반환 구조, Flyway, 트랜잭션 경계 변경 제외. 일반 편집에서 전날 마지막 장소 교체 후 다음 날 첫 양수 이동시간이 남을 가능성은 미실행 가설. 동일 공개 seam의 반례로 확인한 뒤 관련 파생값 무효화 범위만 결정하며 관측된 장애로 기록하지 않음.

요청 컨텍스트는 새 요청마다 생성. 동시에 실행한 두 요청 사이 후보·상세·영양 정보 공유 없음. cache가 정한 출처 사실과 요청별 선택 상태는 구분. 최종 검증 전에 durable Travel 저장 없음, write 단계의 Travel/Plan/일정/음식점 세부 원자성 유지.

## 8. 개인정보·자료 보관

trace에는 단계·실패 종류·시도 번호·비식별 fixture/case ID·최소 후보 자격만 보존. 건강정보·username·JWT·API 키·전체 prompt/response 원문은 공개 로그·Git·Grafana tag로 전송하지 않음. 실제 후보 ID·메뉴를 확인해야 하는 분석 자료는 기존 소독·최소화 절차 적용. 운영 메트릭 label에 candidateId·menuName·자유 오류 문장을 추가하지 않음.

프로젝트 문서와 결과의 보관 사본: `/Users/wooju-kang/Desktop/개발/Yeoro 프로젝트 모음/리팩토링 문서/텍스트 문서`. master `docs/ai/part2-orchestration-plan.md`, `docs/perf/refactoring-plan.md`는 이번에도 스테이징·커밋 금지. 문서 사본의 byte equality 및 기준 SHA 확인.

## 9. 준비 상태와 완료 기준

문서 완료는 2회 이상 독립 review, 최종 9/10 이상·중요 지적 0건, 공식 근거·코드 위치 확인, 보관 사본 일치. 구현 완료는 최소 RED/GREEN·관련 무료 회귀·최종 HEAD CI 통과. 실제 품질 완료는 사전 정의된 항목의 실제 관측 근거와 미측정 항목 분리. 세 상태를 서로 대체하지 않음.

Grafana는 품질 항목별 분모·PASS/FAIL/UNKNOWN/NOT_EVALUABLE과 복구 종류별 결과를 표시하는 보조 시각화. 기존 actual API dashboard는 가져온 관측 값이며 k6 부하 실측으로 표시하지 않음. 새 Grafana 수집을 실행하기 전 평가 원본부터 확보. 문서 설계 단계에 새로운 실측 URL·수치 생성 없음.

## 10. 대안과 판정

| 대안 | 판정 | 이유 |
|---|---|---|
| 전체 correction 횟수 증가 | 기각 | 정상 결과 훼손·호출 증폭 가능, 세부 정답 근거 추가 없음 |
| 기존 슬롯 복구의 자격·실패 분류 정렬 | 우선 | 이미 있는 작은 interface와 예약 implementation 재사용, 호수/공원 반례에 직접 대응 |
| Java 전체 골격 선생성 | 보류 | 기존 보충과 중복, 누락 원인·개선 증거 필요 |
| 날짜별 AI fan-out | 보류 | 중복·지정 장소·경로·ThreadLocal 상태 공유의 새 계약 발생 |
| LLM judge 종합 점수 채택 | 기각 | 출처·슬롯 보존의 결정적 검증 대체 불가 |
| 새로운 PBT·관측 framework 즉시 도입 | 보류 | 먼저 기존 JUnit/registry로 구체적인 반례 확인 가능 |

기술적 근거와 공식 출처는 [조사 문서](p2-selection-quality-followup-research.md)에서 각 주장과 함께 관리. 스킬 지침은 검토 절차이며 외부 API 의미론의 공식 명세로 인용하지 않음.

## 11. Grilling 자동 추천 채택 기록

| Round | 열린 판단 | 추천 채택과 후속 의존 |
|---|---|---|
| 1 | 기준 코드·품질 우선순위 | dev #178 tree 기준, 성공률은 감시·정답/보존은 주 계약. main 유지 |
| 2 | 이미 있는 복구·Java 보충의 재사용 | 국소 재선택 2회·정상 슬롯 예약·식사/복약 policy 유지. 추가 추상화는 실제 두 호출자 필요시에만 검토 |
| 3 | 장소 유형·실패 분류·검증 위치 | 공원 guard 유지, 자격 일치 먼저. 초기 오류 전달은 stable slot 식별 및 최종 검사 보존이 전제 |
| 4 | TDD seam·검증 분모 | 생성 결과·handler 전달·편집 보존·공개 생성/조회. fixture와 실제 모델 품질 근거 분리 |
| 5 | 3·4단계 도입·예산 | 반례가 남은 경우에만 골격/판단 분리 실험. 유료 추가 실행은 별도 승인 |

이전 사용자 지시인 “grilling 추천 자동 채택”을 이번 문서 설계의 결정에 적용. frontier에서 환경 사실은 조사로 해결. 미정 제품 정책(1회분량), 공급자 한도, 추가 유료 실행 승인은 추천으로 만들어낸 사실 또는 승인으로 취급하지 않음.

## 12. 결정과 다음 작업 — 2026-10-11 구현 전 기록

아래 표는 설계 작성 당시의 판단. 이후 2단계 #179 구현·실제 생성 평가를 완료했으며 현재 다음 작업은 §13의 Claude 위임.

| 단계 | 지금 수행한 범위 | 구현 착수 또는 종료 조건 |
|---|---|---|
| 2 | 실패 경로·보존·후보 자격·TDD 계획 구체화 | 공원 자격 불일치의 RED fixture부터 구현, 초기 메뉴 복구는 필요성 재현 후 별도 slice |
| 3 | 기존 Java 보충 조사·골격 도입 조건 정의 | 누락 원인이 남으면 비교 실험, 없으면 도입 보류로 조사 종료 |
| 4 | 판단 의존 관계·분리 조건 정의 | 작은 선택 계약으로 품질상 개선 가능한 증거가 있으면 실험, 없으면 분리 보류 |

추가 사용자 결정 없이 진행 가능한 다음 범위는 위 무료 fixture·코드 조사와 승인된 seam의 최소 구현. 실제 API 호출 비용과 main sync·배포는 기존 승인에서 자동 확장하지 않음.

### 문서 검토 기록

- 독립 1차 8/10: 늦은 식사 보충의 전체 검증 미입증과 편집 fallback의 출처 표현 수정. S10 및 초기 callback 검증 이전 선행 gate 추가
- 독립 2차 9.4/10, Critical 0·Important 0: 문서 gate 통과. 경미 지적의 concurrent 후보 상태와 ThreadLocal collector/tracker 구분 반영
- 점수는 문서의 사실관계·실행 가능성 검토 결과. 실제 모델 품질·무료 테스트 통과·구현 채택 완료와 구분

### 2단계 구현 확인 (2026-10-11)

#179의 공원·초기 메뉴·늦은 보충·부분 편집 예약 반례를 RED/GREEN으로 검증. 최종 무료 회귀 929/929, 독립 리뷰 3회 후 9.3/10·Critical/Important 0. 로컬 구현 상태이며 실제 AI 품질 재측정과 dev 전달은 미실시. 위 설계 작성 시점의 미착수 기록과 구분. 결과·제한: `p2-selection-recovery-stage2-result.md`, 검증 자료: `p2-selection-recovery-stage2-verification.json`.

## 후속 2단계 실제 평가 갱신 (2026-10-11)

실제 생성 12/12·주 gate 및 측정한 감시 기준 충족. 공원 반환 슬롯 0으로 live 공원 선택 개선 미입증. 정상 선택 보존·늦은 식사 출처·부분 편집 예약은 결정적 테스트 근거 유지, 실제 편집 미측정. 정시 필수 식사 93.2%→87.5% 하락과 영양 숫자 채움 45.8% 보존. 응답 단위 교정 4→0 관측을 전체 품질 완료로 확대하지 않음. 3·4단계 자동 착수 없음. 근거: `p2-selection-recovery-stage2-result.md` §6.


## 13. Claude 인수인계 — 후속 3단계 식사·시간표 진단 (2026-10-12)

### 위임 범위와 목표

다음 작업은 기존 후속 3단계의 **Java 필수 골격 필요성 판단 중 식사·시간표 계약 검증**. 필수 식사는 48/48로 채워져 있으므로 새 골격을 먼저 만드는 작업이 아님. 점심 시작이 설정 시각 ±30분을 넘는 6건을 분석하고, 이동/체류 조건상 가능한 일정에서 코드 결함이 재현될 때만 최소 수정. 수정할 결함이 없으면 미수정 근거·골격 도입 보류 판정과 반례 fixture로 종료 가능.

Claude: 식사 시각·시간표 진단, 관련 최소 코드/TDD, 무료 회귀, 결과 문서. Codex: 이후 영양 누락·출처/복구/편집 평가, 실제 API 재측정, Grafana, 후속 AI 의존 분리·최종 보고서. 두 에이전트가 `PlanService`·러너·계획서를 동시에 수정하지 않으며 인수 시 Claude의 쓰기 종료 확인.

### 참고 경로와 기준

- 현 소스 작업 트리(읽기 기준): `/private/tmp/planb-stage2-179`
- 브랜치: `codex/179-refactor-align-travel-park-eligibility-and-failure-local-recovery`
- HEAD/base: `f236f953f6ca3dd55cbb67f1e20142c0fe4ec280` + 미커밋 #179 patch. 원격 dev만 기준으로 사용하면 #179가 빠짐
- 최신 계획: `/private/tmp/planb-stage2-179/docs/ai/part2-orchestration-plan.md` v13, §2.4·순차 인수인계 절
- 설계: `/private/tmp/planb-stage2-179/docs/ai/p2-selection-quality-followup-design.md`
- 현재 결과: `/private/tmp/planb-stage2-179/docs/ai/p2-selection-recovery-stage2-result.md`
- 품질 집계: `/private/tmp/planb-stage2-179/docs/ai/p2-selection-recovery-stage2-actual-quality-20261011.json`
- 원본/오프라인 계수: `/private/tmp/planb-stage2-179/docs/ai/evidence/p2-selection-recovery-stage2-20261011T114549Z/`의 `raw/`, `results.jsonl`, `baseline-results.jsonl`, `supplemental-raw-quality.json`
- 후속 설계 공식 근거(읽기 전용 보관 사본): `/Users/wooju-kang/Desktop/개발/Yeoro 프로젝트 모음/리팩토링 문서/텍스트 문서/p2-selection-quality-followup-research.md`. 현재 temp checkout의 같은 상대경로에는 파일이 없으므로 보관 절대경로 참조
- 현재 구현 patch: `/private/tmp/planb-stage2-179/docs/ai/p2-selection-recovery-stage2-evidence/implementation.patch`
  - SHA256: `67b1dec1b8d2f2ee3741ec370227138ef7dfc649412e13197cbb032972eb63f4`
  - base commit에 적용한 15개 파일이 현재 소스와 byte 단위 동일함을 확인. 착수 시 재확인
- 보관 사본: `/Users/wooju-kang/Desktop/개발/Yeoro 프로젝트 모음/리팩토링 문서/텍스트 문서`

#179 작업 트리에서 checkout/reset/stash/commit하지 않음. 별도 실험 checkout에 고정 base와 patch를 적용하고 적용 직후 파일 hash를 기준으로 기록. `git diff HEAD`는 #179까지 포함하므로 Claude 변경만의 patch로 사용하지 않음. 적용 직후 snapshot과 종료 snapshot을 비교해 식사 작업의 파일·diff만 전달. 새 문서는 식사 진단 결과 1개로 묶고 기존 미추적 계획서는 스테이징 금지. #179 기준이 dev에 고정되기 전 GitHub 전달은 보류하되 오프라인 진단·fixture·최소 patch는 진행.

### 적용 스킬과 순서

1. `diagnosing-bugs`: `/Users/wooju-kang/.agents/skills/diagnosing-bugs/SKILL.md` — 정확한 시간표 반례를 잡는 짧은 실행 루프 먼저 확보. 재현되지 않은 가설로 코드 수정 금지
2. `codebase-design`: `/Users/wooju-kang/.agents/skills/codebase-design/SKILL.md` — 식사 선택, 경로, 시간 정규화, 복약, 최종 검증의 책임과 실제 호출 순서 확인
3. `research`: `/Users/wooju-kang/.agents/skills/research/SKILL.md` — 필요한 Java 시간 처리·Spring 계약만 설치 버전/공식 근거 확인. 도메인 식사 우선순위를 Spring 지침에서 만들어내지 않음
4. `artifact-template-system-design`: `/Users/wooju-kang/.codex/plugins/cache/openai-curated-remote/openai-templates/0.1.1/skills/artifact-template-system-design/SKILL.md` — 기존 식사 module의 interface가 바뀌는 경우에만 파급효과·의존성 설계에 적용
5. `grilling`: `/Users/wooju-kang/.agents/skills/grilling/SKILL.md` — 조사에 기반한 권장 답변 자동 채택. 이동 제약/복약/후처리/편집 영향 검토. 새 제품 정책·유료 실행 승인은 자동 생성하지 않음
6. `tdd`: `/Users/wooju-kang/.agents/skills/tdd/SKILL.md` — 확인된 결함마다 공개 seam의 RED, 최소 수정, GREEN, 다음 반례 순서
7. `review-loop`: `/Users/wooju-kang/.codex/skills/review-loop/SKILL.md` — 최소 2회 독립 리뷰, 최종 9/10 이상·Critical/Important 0건. 실제 품질 개선 완료 점수와 구분
8. `llm-evaluation`: `/Users/wooju-kang/.codex/skills/llm-evaluation/SKILL.md` — 회귀 표본, fixture 정답, 실제 출처의 증거 수준과 분모 구분
9. `caveman`: `/Users/wooju-kang/.agents/skills/caveman/SKILL.md` — 사용자 응답은 간결한 존댓말. 저장 문서는 명확한 일반 문장

스킬 경로가 Claude 환경에서 열리지 않으면 경로를 확인하고 같은 절차를 수행하며, 읽지 않은 스킬을 사용했다고 기록하지 않음. 별도 테스트 framework 의존성 추가는 이번 범위 제외.

### 작업 상한과 종료 계약

이번 한밤 인계의 기본 산출물은 6건의 원본 계수·원인 후보 분류와 대표 원인 1개의 결정적 재현. 새 골격·다수 원인 동시 수정은 제외. 시작 시각과 종료 상한을 기록하고 **최대 3시간 또는 사용자 복귀/중단 지시 중 먼저 도달하는 시점**에 신규 구현을 중지. 코드 수정은 재현된 원인 1개, review-loop는 최대 4회. 이 상한은 추가 유료 예산이나 이후 Codex 작업 상한이 아님.

상한 도달 또는 재현 자료 부족 시 가설·재현 실패 이유·이미 실행한 테스트·미실행 테스트·남은 리뷰·변경 patch를 명시해 인계. 최소 2회 리뷰/9점·무료 회귀를 충족하지 못한 변경은 미검증 실험 상태로 보존하고 채택·완료·병합하지 않음. 6건을 모두 고쳤다고 기록하지 않음. Codex가 다음 세션에서 남은 검증과 Q2를 순차 수행.

### 작업 순서

1. 계획·#179 결과·원본 감사 자료를 읽고 기준 commit/patch/hash, 소유 파일, 단계별 호출 순서를 기록.
2. 이전 3건과 이후 6건의 지연·시각·태그·경로를 재계수. 이후 6건은 서울 2회차 1/2일차(13:28/13:46), 서울 3회차 1/2일차(12:31/12:47), 부산 3회차 1일차(12:45), 강릉 3회차 2일차(14:09). 모두 점심 12:00 기준. 원본은 최종 결과라서 AI 최초 선택·복구 전 시각은 추정하지 않음.
3. 가능한 일정의 정규화 결함, 이동/체류상 불가능한 창, AI 선택/슬롯 순서 영향, 다수 동행인의 창 충돌, 추적 자료 부족으로 분류. 각 원인에 반증 가능한 fixture를 작성하고 실제 코드로 확인.
4. 우선 `/private/tmp/planb-stage2-179/src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java`의 `normalizeScheduleTimes`와 같은 경로의 `PlanService.java`의 생성/finish 순서 확인. `domain/travel/policy/MealSlotPolicy.java`, `ai/handler/MissingSlotCompleter.java`, `domain/health/dto/request/AddCompanionRequest.java`도 검토. 상대 경로 앞에는 현재 별도 실험 checkout의 절대 경로를 사용.
5. 증명된 결함이 있으면 책임 위치의 최소 수정. `PlanService`는 호출 순서 결함이 재현된 경우에만 최소 변경하고 #179 복구 로직·영양 보강은 수정하지 않음. 시각만 변경돼도 메뉴/identity/복약/편집에 미치는 영향을 테스트.
6. 무료 targeted 회귀 후 전체 `test`. 도메인 우선순위 변경이 필요하면 결정 필요 항목으로 보고하고 기존 정책 유지. 새 골격·AI fan-out 자동 구현 없음.
7. 리뷰 gate 충족 후 결과/한계/Claude 변경만의 patch, 재현 명령, 무료 테스트 XML과 소독된 로그, Codex 후속 검증 항목 전달. 실제 API·Grafana 실행은 Codex가 별도 승인 후 담당.

### 테스트와 완료 조건

- 정상 정시 식사 유지; ±30분 경계 포함, 12:31이 창 밖인 경계 사례
- 적용 해제·null 설정·첫 아침/마지막 저녁 면제·다수 동행인 창의 기존 처리 유지
- 앞 장소를 이동해 창 안에 들어갈 수 있는 경우와 물리적으로 불가능한 경우 분리
- 음수 이동시간·시간 겹침·날짜 넘어감의 기존 검증을 약화하지 않는 실패/정상 계약
- 최종 식사 변경에 맞는 복약 시각·`MEAL_TIME_APPLIED` 태그·장소/메뉴 원문 유지
- 공통 시간표 변경 시 편집 요청 밖 날짜 보존, #179 출처/공원/국소 복구 회귀 유지
- private reflection이나 mock 호출 수만으로 식사 품질 증명 금지. 정상인 코드를 일부러 깨뜨려 RED 생성 금지
- 강제 12:00 덮어쓰기, 이동시간 축소, 관광지 삭제, 후보/메뉴 교체로 수치를 올리지 않음
- 결함 수정이면 원인별 반례 GREEN과 보존 계약 충족. 모든 6건의 정시화가 완료 조건은 아님
- 결함이 없거나 자료가 부족하면 조사·보류 결과로 보고. 기존 ±30분 의미/분모·비율/최대 지연 보존

예상 무료 명령(실험 checkout에서 실행):

```bash
./gradlew test --tests 'com.planb.unit.domain.travel.service.ScheduleNormalizerTest' --tests 'com.planb.unit.domain.travel.policy.MealSlotPolicyTest' --tests 'com.planb.unit.domain.travel.service.PlanSelectionRecoveryContractTest' --console=plain
./gradlew test --console=plain
```

새 테스트 클래스는 위 targeted 목록에 추가. 이번 위임은 무료 조사·fixture·최소 수정이며 실제 OpenAI/TourAPI/영양 API 추가 호출, externalTest, 새 Grafana/Prometheus import, 기존 컨테이너/volume 삭제 금지. 다른 작업의 12회 유료 실행 승인은 이월하지 않음.

### 코드·Git·보관·Codex 전달

- Java 체인 각 호출 줄바꿈, 인자 계단식, 3개 이상 선언 매개변수 줄바꿈. 주석은 짧은 명사, `~다/~함`, 불필요한 Javadoc tags 제외. Facade는 Service DI·순차 조립만 유지.
- 이슈가 필요하면 식사 원인/최소 수정으로 별도 제목·영어 본문 준비. #179와 다른 도메인 이슈로 묶음. GitHub 생성·commit·push·PR·dev 병합은 이 위임문만으로 추가 실행 승인으로 간주하지 않으며 사용자 지시가 있으면 그 범위로 실행. main sync는 금지.
- 실행 승인 시 기존 `.github` issue/PR 템플릿 사용. assignee `KangWooJu`, 기존 labels, 확인된 bug는 Bug·그 외 비신규생성 작업은 Feature, Priority/Effort native field 필수. 코드 결함 여부가 아직 미정이면 Issue에서 이를 구분. `type : summary`, PR/merge `#N type : summary`, dev PR은 `Refs #N`, dev 병합 뒤 명시적 Issue close.
- 현재 상태에서 #179를 완료/병합했다고 쓰지 않음. 채택·무료 통과·실제 품질·Git 전달 상태 별도 기록. 계획서 2개는 스테이징·커밋 금지: `docs/ai/part2-orchestration-plan.md`, `docs/perf/refactoring-plan.md`.
- 결과 문서·fixture/patch·소독된 데이터는 `/Users/wooju-kang/Desktop/개발/Yeoro 프로젝트 모음/리팩토링 문서/텍스트 문서`에도 저장하고 byte/hash 동일성 확인. API 키·JWT·전체 실행 환경 출력 금지.
- 마지막 전달: 수정 파일/책임, 원인별 판정과 반례, Claude만의 patch와 전후 hash, 테스트 명령/수/실패, 리뷰 점수/미해결, 기준 채택 필요 여부, 실제 평가에 필요한 fixture·항목·분모, 다음 Codex 작업. Grafana 구현/수집은 수행하지 않음.


### 2026-10-12 인수인계 문서 검토

독립 1차 8.8/10(Critical 0·Important 1·Minor 1): 한밤 작업 상한·미완료 인계 계약 및 과거 §12 시점 보완. 독립 2차 9.3/10(Critical 0·Important 0): 내용 gate 통과. 보관 사본 동기화와 기존 조사 문서의 보관 절대경로 추가는 최종 검증에 포함. 점수는 문서 실행 가능성에 대한 검토이며 실제 늦은 점심 6건의 원인·수정 효과 판정이 아님.


## 14. Claude 식사 진단 인수 결과 (2026-10-12)

§13은 당시 위임문. Claude 쓰기 종료 후 report/patch 원본 hash와 운영 소스 동등성을 확인하고, 식사 6건의 A/B 하한을 재계산해 일치 확인. 새 골격·아침 이동·여유 압축은 보류. 특성 테스트는 결함 수정의 RED/GREEN이 아닌 기존 정책의 공개 seam 검사. 실험 clone에서 Codex가 29건을 재검증했으며 전체 930건은 Claude 보고값으로 미재실행. 원래 #179 소스·테스트에는 Claude patch를 자동 적용하지 않음.

새 평가에서 이전 10/22·이후 9/24의 합쳐진 복약 슬롯이 등록된 식후 30분과 다름. 시작만 검사하는 guard 때문에 전체 구간 겹침도 각각 1건. 기존 복약 22/22·24/24일은 장소 뒤로 미는 현 구현을 포함한 일치 검사이며 등록 간격 준수와 분리. 우선순위 선택 전 운영 정책 수정 제외. 이를 Q6-a/b로 기록하고 HIGH·실제 성분의 미평가와 구분. 등록 시간 계약 진단은 임상적 복약 적정성 판단이 아님.

다음: 사용자에게 등록 간격 유지·장소 체류 중 알림 또는 장소 종료 후 이동·간격 변경 명시 중 일정 정책 확인. 결정을 기다리는 동안 Q2 영양 미채움 진단은 독립 진행 가능. 유료 API·Grafana import·commit·PR·main sync는 이번 인수 범위 제외. 상세 분모는 master v14 §2.4 및 `p2-meal-time-diagnosis-evidence/codex-handoff-crosscheck.json` 참조.
