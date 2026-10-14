# P2-4C 생성 후보 선조회 독립 실험 설계

작성일: 2026-10-10 · Issue #173 · 상태: 설계9.2/10·구현9/10 검토 통과, 무료 회귀875건 통과·실제12회 완료·채택 미달

## 기준과 목적

P2-4A commit `2a99375f1316b2652bf6965e61e2f21a4665afde`에서 독립 실험. P2-4B 미커밋 구현·실측 원본은 별도 작업 트리에서 보존. 후보 상세·영양·경로 Tool을 Java로 옮긴 P2-4B 변경은 포함하지 않음. AI의 지역 관광지·음식점 keyword 검색 왕복을 줄이는 효과만 확인.

## 실행 흐름과 책임

1. `PlanCandidatePrefetcher`(ai/handler): 기존 `TourismTool` 검색 구현 재사용. 관광지 branch에서 지정 장소 조회 후 지역 관광지 필터·무작위 40개 조회. 음식점 branch에서 localFoods, recommendFoods의 빈 값 제거·순서 보존·중복 제거 후 keyword 검색. 두 branch만 동시에 실행, 음식점 keyword는 순차. 기존 검색 구현의 Mono 경로를 추출해 동기 Tool과 선조회가 공유. 지정 장소의 기존 blocking adapter만 Reactor 공식 `fromCallable` + `boundedElastic` 방식으로 격리. 선조회 전체 60초 상한. 지역·keyword 조회의 외부 실패는 전파, 빈 HTTP 응답과 resultCode 실패를 정상 0건으로 숨기지 않음. 지정 장소 lookup 실패는 기존 findPlannedPlaces의 pin 생략 정책 유지.
2. 음식점 검색은 최대 8개 keyword, keyword당 최대 8개 서로 다른 contentId, 전체 최대 40개. 이 상한은 후보 집합 변경이므로 결과에 명시. MealSlotPolicy.configuredMealTime으로 등록된 식사 유형의 여행 전체 개수(첫날 아침·마지막 날 저녁 제외)보다 음식점 수가 작으면 지역 음식점 fallback 1회 수행. keyword가 비어 있고 필수 식사도 0이면 음식점 조회 생략. 실제 메뉴 적합성은 이 단계에서 판단하지 않음. 영양·상세·메뉴 필터 정책은 P2-4A 유지.
3. `PlanCandidateSnapshot`은 불변 검색 결과 목록만 반환. 전체 성공 후 호출 스레드에서 `PlaceCandidateContext`에 기록. 동시 검색 작업은 context와 `NutritionEvaluationCollector`를 변경하지 않음. 관광지·음식점 type 확인과 빈 ID 제외. 지정 장소 pin 보존. 후보 좌표·주소·이미지의 canonical mapping 유지.
4. 생성 전용 `PrefetchedPlanTourismTool`(ai/mcp): `PlanTourismTool` composition. annotated Tool은 findPlaceWithRoute, getRoute, getRestaurantDetail, evaluateFoodNutrition 4개만. 음식점 상세 ID는 현재 후보의 type39 확인 후 사용. 기존 메뉴·영양 판단과 route 의미 유지.
5. `TravelPlanPrompt`: 요청 JSON에 관광지·음식점 후보를 추가하고 제거된 검색 Tool 지시를 수정. `VerifiedPlacePrompt`는 생성에서는 선조회 또는 유지 Tool 후보를 인정하는 계약으로 제공. 편집·재구성의 기존 계약 유지.
6. 초기 AI reset은 생성 Tool이 보관한 후보 seed와 pin을 복원. correction·parse retry는 재선조회 없이 같은 후보 pool 유지. 외부 lookup으로 추가한 cafe 후보는 현재 AI 호출 범위에 속함.
7. `PlanService.recoverPlace`: 초기 생성 AI 완료와 Java 후보 보충 후, 최초 Tool이 찾은 카페·지정 장소를 포함한 전체 후보와 pin을 재선택 seed로 한 번 복사. 매 시도는 그 고정 seed를 복사한 새 context 사용. 최초 카페 C는 두 시도에 유지하고 실패한 첫 시도의 새 후보 D는 두 번째 시도에서 제외. 편집은 기존 새 context 유지. 생성 재선택용 prompt는 후보 pool을 전달하고 지역/keyword 검색을 지시하지 않음. 재선택에서도 4개 Tool, 후보 ID 검증·최대 2회 예산 유지. reset은 호출 직전의 전체 후보와 pin을 보존.
8. 최종 normalization·validation·nutrition 보강·route·write transaction·공개 응답 그대로 유지. 선조회 지역 fallback 완료 여부를 요청 context에 보존해 기존 식사 보충에서 같은 지역 조회를 반복하지 않음. 추가 대표 메뉴 확인·식사 검증은 그대로 수행.

## Grilling 자동 권장 결정

기존 사용자 지시에 따라 모든 권장안을 자동 채택. Round 1: 기준은 P2-4A, 생성 검색 2개만 제거, 나머지 4개 Tool 유지, Facade 변경 없음. Round 2: 요청 범위 후보 snapshot, reset 복원, 생성 재선택 pool 재사용, 편집 분리. Round 3: 검색 concurrency 2, keyword 상한8·후보40·timeout60초, unknown identity 거부, 외부 오류의 기존 AI_TEMPORARILY_UNAVAILABLE 분류 유지. Round 4: 공개 생성·재선택 결과와 편집 응답 및 HTTP stub를 검증 seam으로 사용. 실제 비용 batch는 별도 승인 후 12회. 미정 frontier 없음.

## 테스트 seam과 TDD 순서

기존 승인된 seam인 사용자 생성 결과의 candidate 원본 일치·잘못된 ID 거부·편집 유지에 후보 준비 Interface 추가. 외부 API/time만 mock; 후보 내부 메서드 테스트 금지. 세로 단위로 RED→GREEN.

- 후보 준비 Interface: 지역/keyword 입력, deterministic 외부 응답에서 반환 후보와 pin 관찰. null/빈 keyword, 중복 ID, type 불일치, 상한, 지역 fallback, 예외, timeout/cancellation, 요청 격리.
- 생성·재선택 Interface: prompt 내 후보와 사용자 응답의 canonical name/coordinates 확인. reset·correction 이후 identity 보존, 재선택 unknown candidate 거부, 최초 카페 C 보존·실패 시도 신규 D 격리. edit/rebuild 기존 Tool/응답 보존.
- HTTP integration: 실제 Spring AI Tool 등록 및 schema, stub HTTP 호출, 사용자 REST 생성·조회 결과. 제거한 Tool을 요청하는 구형 stub은 새 생성 계약에 맞춰 수정하며 편집 fixture는 유지.

## 검증과 채택

설계 및 구현 독립 리뷰 각각 최소2회·최대4회, 기준9/10, Critical/Important 미해결 시 통과 금지. 외부 호출 없는 회귀 먼저 완료. 실제 API 4case×3회는 별도 승인 batch로 실행. 품질 R0, 지연·token의 직전 채택 기준 P2-4A. 30% 중앙값 감소, 느려진 case 최대1, token1.1배 이하 및 원 계획 §6.1·§6.3 품질 gate(주 지표: R0 통과 수 대비 감소≤1, 알레르기 추정0·장소중복0, 지정 장소 감소≤1; 감시: 영양·출처 감소≤10%p, HIGH 증가≤10%p, 카페 감소≤50%, 태그 감소≤30%, 이동 증가≤20%, 영업시간 불일치 증가≤2). 추가 batch 승인 없이 자동 확장 없음. 실패 요청 제외 지연과 전체 성공 수를 분리. seed·cache·model·case 조건 기록. stub는 기능/관측 검증이며 AI 지연 효과 증거로 사용하지 않음.

## 위험과 다음 조사

고정 검색어로 모델의 질환·테마별 새 검색 자유도 제거, 상한으로 후보 다양성 감소, 선조회 후보 JSON으로 input token 증가 가능. 요청당2 동시 branch는 공급자 전역 동시성 상한이 아님. 실제 초당 허용치 미확인. P2-4B 실패 요인은 P2-4C 결과 후 별도 분석하며 이번 코드에 실패한 filter 계약을 누적하지 않음.

## 보존

설계·공식 조사·검증 문서와 데이터는 프로젝트와 `/Users/wooju-kang/Desktop/개발/Yeoro 프로젝트 모음/리팩토링 문서/텍스트 문서`에 동일 사본 보관. `.env`·키·원본 외부 응답은 Git 제외. `refactoring-plan.md` 스테이징 금지. 채택 gate 전 commit/push/merge 완료 주장 금지.

## 독립 리뷰 기록

| 대상 | 회차 | 점수 | 조치 |
|---|---|---|---|
| 설계 | 1 | 9/10 | 후보 상한·측정 gate 구체화 |
| 설계 | 2 | 8/10 | 최초 AI 완료 카페를 포함한 재선택 seed 확정 |
| 설계 | 3 | 9.2/10 | gate 통과 |
| 구현 | 1 | 7/10 | 빈 keyword HTTP 응답·잘못된 지정 장소 ID·기존 prompt assertion·취소/격리 검증 보완 |
| 구현 | 2 | 9/10 | Critical/Important 없음, 남은 계단식 서식 반영 |
| 구현 | 3 | 9/10 | 선조회 오류 분류 보완 후 기존 upstream 계약·후보 격리·편집 경로 재확인 |

전체 회귀·HTTP smoke는 무료 검증. 실제 생성12회 완료. 성공11/12·중앙값23.889초·token35,232. 지연·하드 품질 gate 미달, 병합 보류. 결과는 p2-4c-actual-api-result.md 참조.
