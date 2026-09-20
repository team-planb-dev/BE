# PlanB 백엔드 인수인계

Railway v1 배포 이후 진행한 작업의 기록이다. 이 문서 하나로 맥락을 이어받을 수 있도록 썼다.
작업 중인 이슈, 지켜야 할 제약, 다음에 할 일을 담는다.

작성 시점: 2026-09-20
마지막 머지: `3b27a40 Merge pull request #55` (main)
작업 중 브랜치: `56-bug-nutrition-is-blank-for-about-a-third-of-menus-because-restaurant-menu-names-are-not-standard-food`

---

## 0. 먼저 알아야 할 것

### 스택

Spring Boot 4.0.2 / Java 21 / Gradle 9.2.1 / JPA·Hibernate 7.2.1 / Flyway V1–V18 / Railway 배포.
Spring AI 2.0.0 + OpenAI structured outputs + MCP `@Tool`.
테스트는 Testcontainers(MySQL 8.4, Redis 7.2-alpine).

### 테스트 계층과 실행 규칙

| 패키지 | 성격 | 실행 |
| --- | --- | --- |
| `com.planb.unit.*` | 순수 단위 | `./gradlew test --tests 'com.planb.unit.*'` |
| `com.planb.controller.*` | 컨트롤러 슬라이스 | 한 클래스씩 |
| `com.planb.slice.*` | 슬라이스 | **패키지 단위로 하나씩** |
| `com.planb.integration.*` | 통합 | **한 클래스씩**. 컨테이너를 공유하므로 같이 돌리면 깨진다 |

`@Tag("external")`이 붙은 클래스는 실제 외부 API(OpenAI·카카오·한국관광공사·식약처)를 호출한다.
기본 `test` 태스크에서 제외되고 `externalTest`로만 돈다(`build.gradle:142`, `:146`).

현재 `external` 클래스:

- `com.planb.integration.domain.travel.TravelIntegrationTest`
- `com.planb.integration.ai.handler.TravelRecommendHandlerTest`
- `com.planb.integration.ai.service.PlanEditRebuildTest`
- `com.planb.integration.external.*` (Kor2·FoodNtrCpnt·KakaoMap 핸들러)

### 환경변수

`OPENAI_API_KEY`, `KAKAO_REST_API_KEY`, `KOR2_SERVICE_KEY`, `FOOD_NTR_CPNT_URL`, `FOOD_NTR_CPNT_KEY`.
셸 프로필 또는 gitignore된 `.env`에 둔다. **키를 대화나 커밋에 넣지 않는다.**

---

## 1. 지켜야 할 작업 규칙

사용자가 세션 중 명시한 제약이다. 어기면 되돌려야 한다.

1. **브랜치를 직접 만들지 않는다.** 사용자가 이슈와 브랜치를 만들고 체크아웃한 뒤 지시한다.
2. **허가 없이 커밋하지 않는다.** 커밋 명령을 텍스트로 제공만 한다.
3. **프롬프트 텍스트 블록과 `SwaggerConfig` 텍스트 블록은 수정 금지.** 이슈별로 예외를 받는다.
   `src/main/java/com/planb/ai/prompt/` 전체가 대상. 읽기는 자유.
   (#56에서는 예외를 받아 `TravelPlanPrompt`·`EditPlanPrompt`·`TourismTool` `@Tool` description을 수정했다.)
4. 커밋은 **같은 이슈·같은 도메인 카테고리별로 묶어** 나눈다.
   메시지는 영어 Conventional Commits, **제목 한 줄만**, body 없음.
   `git add \` 다음에 파일을 한 줄씩 나열해 바로 실행 가능한 형태로 준다.
5. 이슈·PR 템플릿 **본문은 영어**로 쓴다. 대화는 한국어.
6. 통합 테스트는 한 번에 한 클래스.
7. `Co-Authored-By` / `Claude-Session` 트레일러는 넣지 않는다.
8. "꼭 고쳐야 하는 것만 고친다." 범위를 스스로 넓히지 않는다.

### 진행 방식

사용자가 정착시킨 흐름이다.

```
증상 보고 → /diagnosing-bugs 로 원인 확정 → /research 로 외부 사실 확인
→ /grilling 으로 파급효과 합의 → /tdd 로 빨강→초록
→ 이슈 템플릿 → 사용자가 이슈·브랜치 생성 → 체크아웃 → 구현
→ 커밋 명령 제공 → 사용자가 푸시 → PR 템플릿
```

`.claude/skills/`는 gitignore 대상이라 저장소에 없다. `research` 스킬은 세션 중 내려받았다.

---

## 2. 이 저장소의 AI 일정 생성 흐름

버그 대부분이 이 경로에 모여 있으므로 먼저 익힌다.

```
TravelController
  └ TravelFacade
      └ PlanService.makePlanByAi / makeEditPlanByAi
          ├ TravelRecommendHandler.createPlanByAi   ← OpenAI 호출, Tool 사용
          │     └ TourismTool (@Tool)               ← 관광공사·카카오·식약처
          ├ PlanService.validatePlaces              ← 장소 검증·보정
          │     ├ MissingSlotCompleter.complete     (:743) 빠진 슬롯 채움
          │     ├ TouristPlaceCountPolicy.trimExcess (:751) 관광지 초과분 제거
          │     ├ ScheduleNormalizer                (:761) 시각 확정
          │     └ PlanPlaceResolver.validate        (:776) 슬롯별 검증
          └ PlanService.finishPlan
                ├ fillMissingTravelMinutes
                ├ ScheduleNormalizer (재정규화)
                ├ refillMissingMeals               (:517) 식사 재보정 1회
                ├ validateMealSlots                (:524) 식사 누락 거부
                └ 복약·태그·영양 병합
```

### 알아두면 시간을 아끼는 사실

- `validateMealSlots`는 `finishPlan` 안에 있고, `finishPlan`은 `makePlanByAi`의 `try` **바깥**이다. `ensureDaysRebuilt`의 재시도 `catch`는 `validatePlaces`만 감싼다. **식사 누락은 재시도를 타지 않는다.**
- `ApiExceptionHandler.baseExceptionHandler`에는 `@ResponseStatus`가 없다. 같은 파일의 다른 핸들러에는 있다. 의도된 선택이므로 **비즈니스 예외는 HTTP 200 + `success:false`로 나간다.**
- 편집 경로의 실패는 `ChatController`의 `catch (Exception e)`에서 고정 문구로 바뀐다. 에러 코드도 사유도 사용자에게 전달되지 않는다(`ChatAiReplyMessageHelper.java:26`).
- OpenAI 재시도는 구조적으로 상한이 있다. `OpenAiClient`는 호출당 correction 1회, 재구성은 `attempt < 2`, 식사 재보정은 의도적으로 1회.
- `TravelRecommendHandlerTest`는 `handler.createPlanByAi`를 직접 부른다. **`PlanService`를 타지 않으므로 보정·검증·영양 병합을 재지 못한다.** 이 클래스 로그의 `restaurantDetail` 수치는 AI 원본이다.

---

## 3. 배경 문서

세션 중 조사해 남긴 문서다. **코드를 읽기 전에 먼저 본다.**

| 파일 | 내용 |
| --- | --- |
| `docs/ai/meal-slot-policy.md` | 식사 슬롯 판정 규칙, 소비자 3곳, 하루 길이가 정해지는 과정 |
| `docs/ai/meal-slot-fix-impact.md` | 식사 정책 수정 3안의 파급효과, 깨지는 테스트 전수 |
| `docs/ai/food-name-resolution.md` | 메뉴명이 식약처 품목명과 안 맞는 이유, 접근 방법 비교 |
| `docs/ai/mfds-nutrition-api.md` | 식약처 `getFoodNtrCpntDbInq02` 규격, 분류 축, 1인분 기준 |

### 어떻게 만들었나

넷 다 **조사 전용 서브에이전트**에게 맡겨 쓰게 한 것이다. 방식은 매번 같았다.

1. 코드를 고치기 전에, 답을 모르는 사실을 질문 목록으로 먼저 적는다.
2. 에이전트에게 **읽기 전용**으로 돌린다. 보고서 한 파일 말고는 아무것도 못 고치게 하고,
   `src/main/java/com/planb/ai/prompt/`는 읽기만 허용한다.
3. **모든 주장에 출처를 붙이게 한다.** 저장소 안은 `path:line`, 바깥은 URL.
4. **확인하지 못한 것을 반드시 따로 적게 한다.** 각 문서 마지막 절이 그것이다.
5. 돌아온 보고서에서 **결정을 바꾸는 주장만 골라 내가 직접 다시 확인한다.**

5번이 핵심이다. 에이전트 보고서를 그대로 믿고 코드를 짜지 않는다.
실제로 이 세션에서 에이전트 예측(파손 테스트 8건)과 실측(2건)이 크게 달랐다.

### 무엇이 검증됐고 무엇이 안 됐나

| 주장 | 상태 |
| --- | --- |
| `FoodNtrCpntResponse.Item`에 `Z10500` 없음 (필드 17개) | 직접 확인 |
| `NutritionService` 주석 두 줄이 사실과 다름 | 직접 확인 |
| `41a5309`(PR #48)가 이름 매칭이 아니라 필터를 삭제 | `git show --stat`로 확인 |
| `FoodNtrCpntHelper`의 포함 방향이 한쪽 | 소스로 확인 |
| `DB_CLASS_NM` 하드코딩, `DB_GRP_NM`은 응답 전용 | 소스로 확인 |
| `"검은콩 장칼국수"` 질의가 0건 | 라이브 프로브로 확인. 전체 매칭 규칙은 미확정 |
| `SERVING_SIZE`가 100ml 또는 100g | 공식 공개 데이터 19,495행에서 확인 |
| `Z10500`이 실제 1인분 중량임 | **공식 자료 재검증으로 기각. `식품중량`이며 1인분이 아님** |
| 식약처 오퍼레이션 규격, 타 저장소 활용 사례 | **에이전트 조사. 미검증** |

**에이전트는 실제 API를 한 번도 호출하지 못했다.** 서비스 키가 환경변수로만 들어오기 때문이다.
외부 데이터에 관한 줄은 전부 공개 미리보기·표준데이터·타 저장소 코드에서 끌어온 것이다.

### 어떻게 쓰나

- **`food-name-resolution.md`** — #56 구현의 근거다. 왜 어휘 처리(형태소 분석기·편집거리)를 버리고
  AI 보조명을 택했는지, 어떤 라이브러리를 왜 뺐는지가 적혀 있다.
  이름 쪽을 다시 건드리려는 사람은 여기부터 읽어야 같은 길을 두 번 걷지 않는다.
  실패 8개를 네 유형으로 나눠 뒀으니, 새 실패 사례가 나오면 그 분류에 넣어 보면 된다.

- **`mfds-nutrition-api.md`** — 식약처 조회 규격과 분류 축 조사 기록이다.
  기존의 `Z10500=1인분` 해석은 공식 자료 재검증으로 기각됐다.
  현재 결론은 `mfds-nutrition-api-verification.md`를 우선한다.
  `Z10500`은 식품중량이며 일반적인 1인분 환산 근거로 사용할 수 없다.

- 두 문서 모두 **마지막 절이 "확인하지 못한 것"이다.** 여기를 먼저 보면
  어디까지가 사실이고 어디부터가 추정인지 갈린다. 추정 위에 코드를 짜지 않는다.

---

## 4. 완료된 이슈 (이 세션)

### #49 / PR #50 — 편집이 다른 날의 영양값을 지움

**증상**: 일부 날짜만 재구성하는 편집을 하면, 재구성한 날 **다음 날**의 식사에서 탄수화물·나트륨·지방이 사라졌다.

**원인**: 재구성 날짜 직후 날짜는 첫 이동시간을 다시 계산해야 해서 `finishPlan`을 함께 거치는데, 그 날짜의 메뉴는 이번 호출에서 다시 평가되지 않아 조회 결과가 비어 있었다. 그대로 덮어써서 기존 값이 지워졌다.

**수정**: `PlanService.makeEditPlanByAi`에서 이번 조회 결과 뒤에 저장된 수치를 이어 붙였다. 이번 조회를 우선하고, 못 찾은 메뉴만 저장값으로 되살린다.

머지: `bcb7d65` → `0dd1437`

### #52 / PR #53 — 식사 보정기가 이미 쓴 메뉴를 반복

**증상**: `MissingSlotCompleter`가 빈 식사 슬롯을, 대표메뉴가 이미 일정 어딘가에 쓰인 식당으로 채웠다. 식당 이름이 달라도 같은 음식을 두 끼 먹게 된다.

**수정**: `complete(...)`에 `usedMenus` 집합을 추가했다. 후보의 대표메뉴가 이미 쓰였으면 건너뛰고, 채운 뒤에는 그 메뉴를 집합에 넣는다.

머지: `75c600e`, `aff61f1` → `bd12c9a`

### #54 / PR #55 — 관광지를 자르면 등록한 식사가 사라짐

**증상**: 세 끼를 모두 등록했는데 생성된 하루에 아침도 저녁도 없었다. 거부도 재시도도 없었다.

**원인**: `MealSlotPolicy`가 요구 여부를 **하루 자신의 길이**에서 끌어냈다. 그 날 슬롯들의 가장 이른 시각과 가장 늦은 종료시각 사이에 등록 식사시각이 들어와야 요구했다.
그런데 `validatePlaces` 안에서 식사 보정(`:743`)이 관광지 초과분 제거(`:751`)보다 **앞**이다. 보정은 자르기 전 긴 하루로 판정하고, `trimExcess`가 뒤쪽 `ATTRACTION`을 제거해 하루가 짧아지면 요구 자체가 사라졌다. 소비자 셋 모두 빈 목록을 "이 날은 문제 없음"으로 읽었다.

**수정 — 사양 변경**:

- `MealSlotPolicy.missingMeals(day, healthContexts)` — **채울 대상**. 하루 범위를 보지 않는다. 등록된 식사 중 그 날에 없는 것 전부. `spansConfiguredMeal`·`endOf` 삭제.
- `MealSlotPolicy.requiredMissingMeals(day, healthContexts, totalDays)` — **거부 대상**. 위에서 첫날 `BREAKFAST`와 마지막 날 `DINNER`를 뺀 것. 첫날은 이동 후 시작하고 마지막 날은 귀가로 일찍 끝나므로, 그 두 끼는 채워지면 좋지만 없어도 내보낸다.
- `PlanService.validateMealSlots` — `requiredMissingMeals` 사용. **미사용 음식점 후보가 0이면 즉시 통과**한다. 채울 수 없었던 누락으로 사용자를 빈손으로 돌려보내지 않기 위해서다.
- 전체 일수는 `dateType().getPlusDays() + 1`에서 얻는다. `response.planDays().size()`가 아니다 — 보존 편집 경로는 재구성 날짜만 넘어온다.
- `TravelRecommendHandler.mealSlotFailures`도 `requiredMissingMeals`로 전환. 면제된 끼니 때문에 AI를 다시 부를 값이 없다.
- `MealSlotPolicy` javadoc 갱신. 이전 근거("만들 수 없는 일정을 요구하면 재시도가 끝없이 실패한다")는 현재 코드에서 사실이 아니다.

**남긴 한계 (`ponytail:` 주석)**: `validateMealSlots`는 후보 **개수**만 본다. 보정기는 대표메뉴를 확인하지 못한 후보와 메뉴가 중복인 후보를 건너뛰므로, 그런 후보만 남은 날은 채울 수 없는데도 거부된다.

머지: `6377591`, `36feddd`, `bc52e99` → `3b27a40`

---

## 5. 진행 중 — #56 영양값이 메뉴 3분의 1에서 비어 있음

브랜치 `56-bug-nutrition-is-blank-...`. **아직 커밋하지 않았다.**

### 문제

식당 메뉴명이 식약처 표준 품목명이 아니라서 조회가 실패한다. 실패 사례:

```
검은콩 장칼국수 · 멍게 비빔밥 · 모듬생선구이 · 알곤이칼국수
영양솥밥+생선구이 · 초당두부밥상 · 초당순두부·두부전골 · 한우광양불고기
```

**이 증상으로 이슈가 열린 것은 세 번째다.** `#43`(PR #44)는 AI가 준 값 대신 조회값을 쓰게 했고, `#47`(PR #48, 커밋 `41a5309`)은 필터를 지워 후보를 더 남겼다. **둘 다 이름 매칭 자체는 건드리지 않았다.**

### 확정된 사실

- 현재 PlanB `02` 조회 경로에서 `칼국수`는 정확히 같은 이름 3건, `장칼국수`와 `검은콩 장칼국수`는 0건이다.
  → 현재 계약에서는 `장칼국수`가 아니라 조회 가능한 기본 음식명 `칼국수`를 전달해야 한다.
  → 공식 규격에는 완전일치·부분일치 방식이 없으므로 전체 검색 알고리즘까지 단정하지 않는다.
- `FoodNtrCpntHelper.normalizeFoodName`은 밑줄(`_`) 뒤만 남긴다. 공백·`+`·`·`는 보지 않는다.
- 포함 필터는 `표준명.contains(검색어)` 방향이라, 검색어가 표준명보다 길면 무조건 탈락한다.
- `FoodNtrCpntSearchRequest.of`는 `DB_CLASS_NM`을 `"품목대표"`로 **하드코딩**한다.
- `FoodNtrCpntResponse.Item.servingSize`(`SERVING_SIZE`)는 파싱되지만 **아무도 쓰지 않는다.** `NutritionService.toServing`이 무조건 `× SERVING_RATIO(3.0)` 한다. `REFERENCE_SERVING_GRAMS = 300.0`은 "품목대표는 100g 기준"이라는 전제 위에 있다.

### 구현한 것 (커밋 전)

**`NutritionService`** — 표준 품목명으로 재조회하는 경로 추가.

```java
public Mono<NutritionEvaluationResult> evaluateFoodNutrition(
        String foodName,
        String standardFoodName,
        List<DiseaseType> diseaseTypes
) {

    return lookup(foodName)
            .flatMap(items -> items.isEmpty() && retryable(foodName, standardFoodName)
                    ? lookup(standardFoodName)
                        .map(retried -> evaluated(retried, standardFoodName, diseaseTypes))
                    : Mono.just(evaluated(items, foodName, diseaseTypes)))
            .onErrorResume(...);
}
```

2-인자 오버로드는 `null`을 넘겨 기존 동작을 유지한다.
`retryable`은 표준명이 비었거나 메뉴명과 같으면 false — 같은 조회를 두 번 하지 않는다.
**타임아웃은 `lookup()` 안, 즉 조회 한 건마다 건다.** 처음에는 `flatMap` 바깥에 한 번만 걸었는데, 재조회가 첫 조회의 예산을 갉아먹어 운영 로그에서 타임아웃이 쏟아졌다(아래 참조).

**`TourismTool`** — `@Tool` 파라미터에 `standardFoodName` 추가. description에 실패 사례 예시를 넣었다.

> ⚠ **함정**: `nutritionEvaluationCollector.record(foodName, result)`의 키는 **원래 메뉴명이어야 한다.**
> `PlanService:1228`(수치)과 `:1357`(참고 태그)이 `restaurantDetail.menuName()`으로 되찾는다.
> 여기에 표준명을 넣으면 조회는 성공하는데 화면은 그대로 빈칸이 된다. 증상이 지금과 똑같아 찾기 어렵다.
> `TourismToolTest`의 `표준 품목명을 조회에 넘기되 기록은 메뉴명으로 남김`이 이걸 고정한다.

**`PlanTourismTool`** — 위임 시그니처.

**`TravelPlanPrompt` / `EditPlanPrompt`** — 표준 품목명을 항상 함께 내라는 지시. 예시 포함. 복합 메뉴는 주된 음식 하나만. 모르면 메뉴명 그대로, 지어내지 말 것.

### 실측 — 프롬프트는 작동, 조회는 여전히 실패

`TravelIntegrationTest` 실행 로그에서 모델이 파라미터를 정확히 채웠다.

```
검은콩 장칼국수       → 장칼국수
순옹심이             → 감자옹심이
영양솥밥+생선구이     → 생선구이        (복합 → 하나, 지시대로)
가양칼국수버섯매운탕  → 칼국수
광양 불고기          → 불고기
초당두부밥상         → 초당두부밥상     (모르면 그대로, 지시대로)
```

그런데 `검은콩 장칼국수` → `장칼국수` 재조회는 0건이었다.
후속 프로브에서 `칼국수`는 3건이 조회돼, Prompt와 Tool 예시를 `검은콩 장칼국수` → `칼국수`로 정정했다.
AI가 조회 가능한 기본 음식명을 고르지 못하면 실패하는 점은 이 수정의 천장이다.

### 회귀와 그 수정

같은 실행에서 `TimeoutException`이 6건 났다. `설렁탕`·`삼계탕`은 **직전 실행에서 성공했던 메뉴**다.

```
00:57:21 초당순두부·두부전골 → 12.7초  (재조회 경로, 턱걸이 성공)
00:57:43 순옹심이           → 15.0초  (재조회 경로, 타임아웃)
```

주석이 "정상 응답도 4~6초"라고 적어둔 API를 두 번 부르면 15초 예산을 다 먹는다.
→ 타임아웃을 `lookup()` 단위로 내렸다. 최악 30초로 늘지만 재조회가 첫 조회를 굶기지 않는다.

`NutritionServiceTest`의 `첫 조회가 오래 걸려도 재조회는 자기 시간을 새로 받는다`가 이걸 고정한다.
가상시간(`StepVerifier.withVirtualTime`)을 쓰므로 **Mock의 `Mono`는 `thenAnswer`로 지연 생성해야 한다.** `thenReturn`으로 미리 만들면 실제 스케줄러를 잡아 테스트가 거짓 실패한다.

### 타임아웃 수정 검증 완료 — 회귀가 맞았다

수정 후 `TravelIntegrationTest`를 다시 돌렸다. **`TimeoutException` 0건**(직전 6건).

| 메뉴 | 수정 전 | 수정 후 |
| --- | --- | --- |
| `설렁탕` | 15.0초 타임아웃 | 5.8초 성공, 탄수 0.36 / 나트륨 22.0 |
| `삼계탕` | 15.0초 타임아웃 | 4.0초 성공, 3.84 / 82.0 |
| `고등어구이` | — | 4.2초 성공, 2.69 / 207.0 |

외부 API 지연이 아니라 **재조회가 첫 조회 예산을 갉아먹은 것**이 원인이었다.

### #56 수정이 작동한다는 첫 증거

```
[AI TOOL] 영양정보 평가 호출 - foodName: 한우광양불고기, standardFoodName: 불고기
→ carbohydrate: 26.96, sodium: 253.0, fat: 5.31
```

메뉴명으로는 나오지 않던 값이 표준 품목명 재조회로 채워졌다. 소요 8.6초 — 두 번 조회한 시간이다.
`순옹심이`→`감자옹심이`, `물회국수`→`물회`도 이번엔 성공했다(직전 실행에서는 실패).

후속 실행에서는 `검은콩 장칼국수`와 `장칼국수` 모두 `칼국수`로 재조회돼 최종 병합에 성공했다.
서울에서 AI가 선택한 `삼계탕`, `칼국수`, `설렁탕`, `고등어구이`, `광양 불고기`도 실제 수치가 반영됐다.

남은 누락은 두 부류다.

- `영양솥밥+생선구이`처럼 표준 품목명으로도 조회 결과가 없는 메뉴
- `초당두부밥상`, `멍게 비빔밥`, `한우광양불고기`처럼 `MissingSlotCompleter`가 AI 호출 뒤 추가한 메뉴

두 번째 부류는 #56의 이름 fallback과 다른 실행 순서 문제이므로 별도 이슈로 넘긴다.

### 테스트 상태

| 대상 | 결과 |
| --- | --- |
| `com.planb.unit.*` | 466개, 0 실패 |
| `TravelValidationIntegrationTest` | 0 실패 |
| `ChatAiEditPlanIntegrationTest` | 0 실패 |
| `TravelIntegrationTest` | 통과 |
| `TravelRecommendHandlerTest` | 통과 |
| `PlanEditRebuildTest` | 통과 |
| `com.planb.slice.*` · `com.planb.controller.*` | 변경 클래스 참조 0건 |

`TravelRecommendHandlerTest`는 `PlanService`를 거치지 않으므로 최종 영양 병합의 증거로 사용하지 않는다.
실제 production 경로의 확인 근거는 `TravelIntegrationTest`다.

### 커밋 명령 (아직 실행 안 함)

```bash
git add \
  src/main/java/com/planb/domain/travel/service/NutritionService.java \
  src/test/java/com/planb/unit/domain/travel/service/NutritionServiceTest.java
git commit -m "feat: look up nutrition again with a standard food item name"
```

```bash
git add \
  src/main/java/com/planb/ai/mcp/TourismTool.java \
  src/main/java/com/planb/ai/mcp/PlanTourismTool.java \
  src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java \
  src/main/java/com/planb/ai/prompt/EditPlanPrompt.java \
  src/test/java/com/planb/unit/global/ai/prompt/PlanDayRebuildContractTest.java \
  src/test/java/com/planb/unit/global/ai/tool/TourismToolTest.java
git commit -m "feat: ask the model for a standard food item name alongside the menu name"
```

```bash
git add \
  docs/ai/food-name-resolution.md \
  docs/ai/mfds-nutrition-api.md \
  docs/ai/mfds-nutrition-api-verification.md \
  docs/ai/HANDOFF.md \
  src/test/java/com/planb/integration/external/foodNtrCpnt/FoodNtrCpntHandlerTest.java
git commit -m "docs: record how menu names are resolved against the nutrition API"
```

세 번째에 들어가는 `FoodNtrCpntHandlerTest` 변경은 부분일치 여부를 확인한 프로브 테스트다.
사용자가 남기기로 했다. 식약처가 나중에 항목을 추가하면 의미가 달라지므로 단언은 넣지 않았다.

---

### `DB_CLASS_NM` 확장 검토 — 하지 말 것

"분류를 더 세분화해 재조회하면 찾히지 않겠나"를 검토했고 **답은 아니다**. 축이 다르다.

- `DB_CLASS_NM` 값은 **`품목대표` / `상용제품` 둘뿐**이다.
- 음식 / 가공식품 / 원재료성식품 / 건강기능식품 축은 **`DB_GRP_NM`**이고, **요청 파라미터에 없다.** 응답 전용이며 `FoodNtrCpntHelper`가 이미 `"음식"`으로 거르고 있다.
- `DB_CLASS_NM`을 생략하면 전체의 93%인 가공식품 행이 `numOfRows=100`을 먼저 채우고 헬퍼가 다 버린다. **넓히면 나빠진다.**
- 실패 원인은 분류가 아니라 이름이다. 이 DB의 음식 식품명은 `대표식품명_수식어` 형태다(공식 샘플 `국밥_돼지머리`, 실데이터 `김치순두부_바지락`).

근거는 `docs/ai/mfds-nutrition-api.md`.

### 규격 관련 미확인

- 저장소는 `getFoodNtrCpntDbInq02`를 부르는데(`FoodNtrCpntHandler.java:33`), **공공데이터포털의 현행 문서는 `03`이다.** 02 언급이 포털에 남아 있지 않다. 마이그레이션 필요 여부 미확인.
- `FOOD_NM_KR`의 완전일치·부분일치 방식은 **규격에 표기가 없다.** 현재 `02` 프로브에서는 `칼국수` 3건, `장칼국수` 0건, `검은콩 장칼국수` 0건이었다.
- `03`에서도 같은 결과인지, `02`와 응답 스키마가 같은지는 별도 마이그레이션 조사 대상이다.

---

## 6. 아직 열지 않은 이슈

우선순위 순.

### (0) 서빙 환산 정책 재정의 — 구현 전 추가 결정 필요

`NutritionService`는 모든 영양값을 고정 `× 3.0`으로 평가한다. 이 값이 정확한 1인분이라는 근거는 없다.
다만 기존 인수인계가 제안한 `Z10500 / SERVING_SIZE`도 일반적인 1인분 환산으로 사용할 수 없다.

공식 자료 재검증 결과:

- `SERVING_SIZE`는 `영양성분함량기준량`이다.
- `Z10500`은 `식품중량`이며 1인분으로 정의되지 않는다.
- `DISH_ONE_SERVING`은 별도의 `1회분량 참고량` 필드다.
- 공식 공개 음식 데이터에서는 1회분량 참고량이 비어 있고, 식품중량에는 다인용 조리세트 전체 중량도 섞인다.

따라서 전역적으로 `AMT_NUMx × (Z10500 / SERVING_SIZE)`를 적용하면 안 된다.
1인분 정책과 출처를 먼저 정한 뒤 별도 이슈로 다룬다.
근거는 `docs/ai/mfds-nutrition-api-verification.md`다.

### (0-b) 보정기가 채운 식사는 영양 조회를 아예 거치지 않는다

`TravelIntegrationTest` 로그에서 새로 확인했다.

```
11:12:39 [SLOT FILL] 식사 슬롯 보정 - scheduleType: LUNCH, locationName: 황도바지락칼국수
11:12:39 [SLOT FILL] 식사 슬롯 보정 - scheduleType: LUNCH, locationName: 하니칼국수
11:12:43 영양성분 조회 실패 - menuName: 칼국수
11:12:43 영양성분 조회 실패 - menuName: 알곤이칼국수
```

두 메뉴에 대한 `[AI TOOL] 영양정보 평가 호출`이 **로그 어디에도 없다.**

영양 조회는 AI가 `evaluateFoodNutrition` Tool을 부를 때만 일어난다.
`MissingSlotCompleter`는 AI 호출이 끝난 뒤 슬롯을 채우므로, 그 메뉴는 `NutritionEvaluationCollector`에 항목이 없다.
`PlanService`의 merge는 조회 결과가 없으면 `null`을 넣는다(`PlanService.java:1245`).

**즉 보정기가 채운 식사는 구조적으로 영양값이 항상 빈칸이다.** #56 수정과 무관하게 남는 구멍이고,
#55 이후 보정기가 식사를 더 자주 채우므로 빈도가 올라간다.

고치려면 `MissingSlotCompleter`가 슬롯을 채운 뒤 `NutritionService`를 직접 부르거나,
`PlanService`가 merge 직전에 수집기에 없는 메뉴를 한 번 더 조회해야 한다.
후자가 seam이 하나뿐이라 작다.

### (1) 거부 정밀도 — #55가 만든 위험

`PlanService.validateMealSlots`가 미사용 음식점 후보 **개수**만 본다. 보정기가 건너뛴 후보(대표메뉴 미확인, 메뉴 중복)만 남은 날은 채울 수 없는데도 거부된다. 운영에서 거부 빈도가 올라갈 수 있다.
코드에 `ponytail:` 주석으로 명시해 두었다.
정확히 고치려면 `MissingSlotCompleter`가 시도 결과를 돌려주게 해야 하는데, 반환형이 세 호출부로 번진다.
되돌리기는 쉽다 — 게이트 한 줄.

### (2) 식사 슬롯 삽입 후 낡은 `travelMinutes`

`MissingSlotCompleter`가 `configuredMealTime`을 검사 없이 시작시각으로 쓴다. 아침을 08:00에 꽂으면 재정렬로 맨 앞에 간다. 그런데 **뒤 슬롯의 `travelMinutes`는 낡은 값 그대로다.** `PlanService.fillScheduleTravelMinutes`가 `null` 또는 `0`일 때만 다시 조회하기 때문이다.
원래 있던 문제지만 #55 이후 이 경로를 자주 탄다. 크래시는 아니고 이동시간이 틀리게 보인다.

### (3) 하루가 좁아지는 경우를 재는 통합 테스트 없음

`TravelIntegrationTest`가 우연히 실행했지만 단언하지 않는다. 가드는 단위 테스트뿐이다.
`MealSlotPolicy`에 하루 범위 검사를 되돌려 넣어도 통합에서는 잡히지 않는다.

### (4) 편집 경로 거부의 사용자 경험

편집에서 거부가 나면 사용자는 `"일정 수정 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요."` 한 줄만 본다. 어느 끼니인지, 왜인지 전달되지 않는다(`ChatController.java:39`, `ChatAiReplyMessageHelper.java:26`).

### (5) `SODIUM_REFERENCE` 태그가 값 없이 붙는 것 — 재현되지 않음, 우선순위 낮춤

2026-09-20 `TravelIntegrationTest` 로그에서 반례를 찾지 못했다.
`황도바지락칼국수`는 영양값이 전부 `null`인데 태그는 `["LOCAL_FOOD", "ALLERGY_CHECK"]`뿐이고 영양 태그가 없다.
값이 있는 `광양불고기본가`에만 `CARBOHYDRATE_REFERENCE`·`SODIUM_REFERENCE`가 붙었다.

태그는 `detail.nutritionType()`에서 나오므로(`PlanService.java:1363`) 구조상 가능은 하지만,
**관측된 사례가 없다.** 다시 보이기 전까지는 열지 않는다.

### (6) Vercel preview URL origin (CORS) — 재확인 필요

이전 세션 발견. 현재 코드 기준으로 다시 확인하지 않았다.

### 폐기된 항목

`resolveStandardFoodName` — **현재 코드에 그런 심볼이 없다.** 이전 메모가 낡았다.

---

## 7. 다음 사람이 바로 할 일

1. `FoodNtrCpntHandlerTest` 프로브 완료. `칼국수` 3건, `장칼국수` 0건, `검은콩 장칼국수` 0건.
   Prompt와 Tool 예시는 조회 가능한 기본 음식명 `칼국수`로 정정했다.
2. #56 커밋·푸시·PR. `external` 3종은 타임아웃 수정 후 재실행해 통과를 확인했다.
3. **(0-b) 보정 식사 영양 조회 누락**을 먼저 연다.
   서빙 환산은 `Z10500=1인분` 가정이 기각됐으므로 정책 재정의 뒤 별도 이슈로 연다.
4. 나머지 (1)~(6) 이슈 생성.

### PR 본문에 넣을 정정

`#54` 이슈 본문은 `TravelRecommendHandlerTest`의 경주 2일차를 증거로 인용했다. **그것은 증거가 아니다.**
그 클래스는 `handler.createPlanByAi`를 직접 부르므로 `MissingSlotCompleter`·`validateMealSlots`에 닿지 않는다.
실제 증거는 `TravelIntegrationTest` 로그의 `[SLOT FILL] 식사 슬롯 보정 - scheduleType: BREAKFAST` 두 건이다.
