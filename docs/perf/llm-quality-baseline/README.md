# Travel AI LLM 품질 기준선 — Phase 0B

- Issue: #90
- 기준 commit: `3cb20f1` (Round 2 시작 시점의 `origin/dev`)
- 실행기: `src/test/java/com/planb/integration/domain/travel/TravelLlmQualityBaselineTest.java`
- 상태: 케이스와 기록 형식 확정. **실제 실행 결과는 아직 없다** (6절에 기록한다)

## 1. 목적

계획 생성은 비결정적이다. Phase 0A 하네스(#86)는 고정된 AI 응답으로 Java 계약을 증명하지만,
실제 모델 출력의 품질이 바뀌었는지는 알 수 없다.

이 기준선은 이후 성능 변경(관측, 가상 스레드, 캐시 등) **전에** 실제 모델의 품질 분포를
기록해 둔다. 변경 후 같은 실행을 반복해 차이가 정상 편차인지 회귀인지 구분하는 데 쓴다.

**한 번의 실패는 regression이 아니다.** 판정은 반복 실행의 통과율과 편차로만 한다.
이 기준선은 PR 필수 gate가 아니다.

## 2. 골든 케이스

모든 케이스는 `ONE_NIGHT_TWO_DAYS`(1박 2일)다. 판정에 쓰는 `TravelPlanAssertions.assertPlan`이
2일 일정을 전제로 검사하기 때문이다. 일수를 바꾸는 케이스는 그 도우미를 확장한 뒤 추가한다.

| ID | 지역 | 이동 | 스타일 | 테마 | 동행인 질환 | 지정 장소 | 현지 음식 |
|---|---|---|---|---|---|---|---|
| C1 | 서울 종로구 (광역시) | TRANSIT | MATCH_MEAL_TIME | TASTE | [당뇨] | 경복궁 | 설렁탕 |
| C2 | 부산 해운대구 (광역시) | CAR | LESS_WALK | NATURE | [고혈압], [이상지질혈증] | 해동용궁사 | 돼지국밥 |
| C3 | 강원특별자치도 강릉시 (도) | TRANSIT | LESS_TOURISM | NATURE | [당뇨, 고혈압] | 경포대 | 초당순두부 |
| C4 | 경상북도 경주시 (도) | CAR | MATCH_MEAL_TIME | HISTORY | [당뇨, 이상지질혈증], [고혈압] | 불국사 | 쌈밥 |

케이스가 나눠 보는 축:

- 지역: 광역시(C1, C2)와 도(C3, C4)
- 질환: 단일(C1, C2), 복합(C3, C4)
- 동행인 수: 1명(C1, C3), 2명(C2, C4)
- 이동과 스타일: 네 조합이 서로 겹치지 않는다

모든 동행인의 공통 조건: 걷기 `MODERATE`, 아침 08:00·점심 12:00·저녁 18:00, 새우 알레르기,
점심 식후 30분 복약 1건. 추천 음식은 불고기·비빔밥·칼국수·삼계탕·생선구이로 고정한다.

반복: 케이스마다 3회, 총 12회. 출발일은 실행일 기준 7일 후다.

## 3. 실행 절차

### 3-1. 실행 전 확인

기준선은 **운영 코드가 `3cb20f1`과 같은 상태**에서 실행한다. Round 2의 계측 변경(Phase 1-b)이
`dev`에 먼저 병합되면 `dev`의 운영 코드가 달라지므로, 이 실행기를 추가한 커밋에서 분리
worktree를 만들어 실행한다.

```bash
cd <main-worktree>
git fetch origin --prune
git worktree add --detach ../planB-baseline <이 실행기가 들어간 커밋>
cd ../planB-baseline

# 운영 코드가 기준 commit과 같은지 확인한다. 출력이 없어야 한다.
git diff --stat 3cb20f1 HEAD -- src/main build.gradle
```

출력이 있으면 실행하지 않는다. 기준 commit과 다른 코드로 잰 결과는 기준선이 아니다.

### 3-2. 실행

외부 API 키가 설정된 환경에서 Gradle로 실행한다. IDE의 JUnit 실행기는 `external` 태그 태스크를
거치지 않는다.

```bash
./gradlew externalTest --tests "com.planb.integration.domain.travel.TravelLlmQualityBaselineTest"
```

- 계획 생성 12회를 실제로 호출한다. 유료이며 사용자가 실행한다
- `OPENAI_API_KEY`가 없으면 12회 모두 건너뛴다(skipped). 키 없이 돌리면 앱은 기동되지만
  모든 실행이 `AI_TEMPORARILY_UNAVAILABLE`로 기록되어 모델 장애처럼 보이기 때문이다
- 품질 결과로는 테스트가 실패하지 않는다. 테스트 실패는 실행기 자체의 문제다

### 3-3. 함께 기록할 값

| 값 | 얻는 방법 |
|---|---|
| 실행 commit | `git rev-parse HEAD` |
| 기준 commit과 운영 코드 동일 여부 | 3-1의 `git diff --stat` 결과 |
| 모델 | 실행 환경의 `OPENAI_MODEL` 값 (키는 기록하지 않는다) |
| Prompt | 실행 commit의 `src/main/java/com/planb/ai/prompt/` (commit으로 고정된다) |
| 실행 시각 | 결과 파일의 `runId` (UTC) |
| 외부 API 상태 | 실행 중 `API_FAILURE` 건수와 사유 |

## 4. 결과 형식

실행기는 두 종류의 파일을 `build/llm-quality-baseline/` 아래에 남긴다. `build/`는 Git이 추적하지
않으므로 **어느 것도 커밋되지 않는다.**

| 파일 | 내용 |
|---|---|
| `results-<runId>.jsonl` | 실행 1회당 한 줄의 요약 |
| `raw/<runId>/<caseId>-<repeat>.json` | API 응답 원문 (원인 조사용) |

`results-<runId>.jsonl`의 한 줄:

| 필드 | 뜻 |
|---|---|
| `runId` | 실행 묶음 식별자. 시작 시각(UTC) |
| `caseId`, `repeat` | 케이스와 반복 번호(1~3) |
| `outcome` | `PASS`, `INVARIANT_FAILURE`, `API_FAILURE` |
| `durationMs` | 계획 생성 요청 1건의 소요시간 |
| `failure` | 실패 사유의 첫 줄(최대 200자). 계획 원문은 넣지 않는다 |
| `restaurantCount` | 음식점 슬롯 수 |
| `nutritionFilledCount` | 그중 탄수화물 값이 채워진 슬롯 수 |
| `rawFile` | 원문 파일 이름 |

`outcome` 판정:

- `API_FAILURE`: 응답의 `success`가 `false`. 모델 거부, 외부 API 오류 등
- `INVARIANT_FAILURE`: 응답은 성공했으나 `TravelPlanAssertions.assertPlan`이 실패.
  검사 항목은 다음과 같다
  - 2일 구성과 날짜
  - 슬롯 시작·종료 시각 순서
  - 장소 중복, 메뉴 중복
  - 이동시간 값
  - candidate identity
  - 음식점 슬롯의 식사 종류(아침·점심·저녁), 주소, 좌표
  - 음식점이 아닌 슬롯의 코스 종류
  - MEDICATION 슬롯의 설명과 태그
- `PASS`: 위 검사를 모두 통과

`assertPlan`은 **필수 식사가 빠짐없이 있는지와 관광지 개수 정책은 보지 않는다.** 이 둘은
생성 과정의 `MealSlotPolicy`와 `PlanService` 검증이 다루며, 검증이 거부하면 응답이
`API_FAILURE`로 나온다. 음식점 후보가 부족해 정책상 허용된 식사 누락은 `PASS`로 집계될 수 있다.

## 5. 집계와 판정 기준

### 5-1. 집계

| 지표 | 계산 |
|---|---|
| 케이스별 통과 수 | `PASS` 건수 / 3 |
| 전체 통과 수 | `PASS` 건수 / 12 |
| 케이스별 편차 | 3회 결과가 모두 같은지, 섞였는지. 섞였다면 어떤 `outcome`이 몇 번인지 |
| 실패 유형 | `failure` 첫 줄을 같은 원인끼리 묶은 건수 |
| 영양정보 채움률 | `nutritionFilledCount` 합 / `restaurantCount` 합 (참고값) |
| 소요시간 | 케이스별 최소·중앙값·최대 (참고값) |

케이스당 3회는 작은 표본이다. 비율을 소수점으로 해석하지 않고 건수로 본다.

### 5-2. 이후 실험의 판정 기준

성능 변경 뒤 같은 12회를 다시 실행해 이 기준선과 비교한다. 기준은 실행 **전에** 정해 둔다.

**회귀로 보고 중단·조사하는 경우** (하나라도 해당):

1. 전체 통과 수가 기준선보다 **3건 이상** 적다
2. 기준선에서 2/3 이상 통과한 케이스가 **0/3**이 된다
3. 기준선에 없던 실패 유형이 **2회 이상** 나온다

**회귀로 보지 않는 경우**:

- 단일 실행의 실패
- 전체 통과 수 차이가 2건 이하
- 영양정보 채움률과 소요시간의 변화. 이 둘은 참고값이며 성능 판정은 Phase 3 부하 측정으로 한다

`API_FAILURE`가 외부 API 장애나 쿼터 때문으로 확인되면 해당 실행은 집계에서 제외하고 사유를
남긴다. 제외가 3건을 넘으면 그 실행 묶음 전체를 기준선으로 쓰지 않고 다시 실행한다.

## 6. 기준선 결과

실행 후 아래를 채운다. 원본 로그와 응답은 붙이지 않는다.

| 항목 | 값 |
|---|---|
| 실행 commit | |
| 기준 commit과 운영 코드 동일 | |
| 모델 | |
| runId | |
| 전체 통과 수 | / 12 |
| 제외한 실행 (사유) | |

| 케이스 | 통과 | 결과 구성 | 주요 실패 유형 | 영양 채움 | 소요시간 중앙값 |
|---|---|---|---|---|---|
| C1 | /3 | | | | |
| C2 | /3 | | | | |
| C3 | /3 | | | | |
| C4 | /3 | | | | |

## 7. 한계

- 모든 케이스가 1박 2일이다. `assertPlan`이 2일을 전제로 한다
- 영양정보 채움은 탄수화물 값의 존재로만 센다
- 케이스당 3회는 작은 표본이라 판정 기준도 건수 단위로 거칠게 잡았다
- 실행기 검증: API 키 없이 실행해 12회가 펼쳐지고 결과 파일이 기록되는 경로까지 확인했다.
  실제 모델 응답을 받은 실행은 아직 없다
