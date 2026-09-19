# AI 일정 식사 슬롯 판정

`MealSlotPolicy`가 "이 날에 이 식사가 있어야 하는가"를 단독으로 정한다. 채우는 쪽(`MissingSlotCompleter`), 재시도를 요구하는 쪽(`TravelRecommendHandler`), 거부하는 쪽(`PlanService.validateMealSlots`)이 모두 같은 판정을 쓰므로, 판정이 "요구 없음"을 돌려주면 세 경로가 동시에 조용해진다.

- 요구 기준은 하루의 시작·종료 시각이다. 그 날 슬롯들의 가장 이른 `startTime`을 `dayStart`, 가장 늦은 종료시각을 `dayEnd`로 잡고(`src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:61`, `src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:67`), 등록 식사시각이 그 구간 안에 들어올 때만 그 식사를 요구한다(`src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:131`). 경계는 양끝 포함이다.
- 종료시각이 없는 슬롯은 `startTime`을 종료시각으로 본다(`src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:161`).
- 판정 대상은 `BREAKFAST`, `LUNCH`, `DINNER` 세 가지뿐이다(`src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:22`). 동행인이 없거나 `schedules`가 비면 판정 자체를 하지 않고 빈 목록을 돌려준다(`src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:43`, `src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:57`).
- 동행인이 여러 명이면 요구 여부는 "한 명이라도 범위 안"(`anyMatch`, `src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:131`)이고, 채워 넣을 시각은 "가장 이른 시각"(`min`, `src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:113`)이다. 두 기준이 다르다.
- `mealInfo.applied()`가 false이거나 끼니별 `*Applied()`가 false이면 그 식사는 등록되지 않은 것으로 보고 요구하지 않는다(`src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:140`).

## 소비자와 빈 목록일 때의 동작

세 소비자 모두 빈 목록을 "이 날은 문제 없음"으로 읽는다. 요구 없음과 충족을 구분하지 않는다.

- `TravelRecommendHandler.mealSlotFailures`는 누락 식사마다 교정 사유 문자열 하나를 만든다(`src/main/java/com/planb/ai/handler/TravelRecommendHandler.java:272`). 문구는 `"planDays[dayN].schedules: <mealType> 식사 슬롯 필요 / 실제 없음 / 등록 식사시각을 지나는 일정이므로 음식점 후보로 식사 슬롯 추가"`다(`src/main/java/com/planb/ai/handler/TravelRecommendHandler.java:342`). 빈 목록이면 사유가 없고 재시도도 없다.
- 사유가 있어도 이번 호출의 미사용 음식점 후보 수만큼은 `unfillable`이 덜어낸다(`src/main/java/com/planb/ai/handler/TravelRecommendHandler.java:293`). Java가 채울 수 있는 만큼은 AI 재시도로 넘기지 않는다는 뜻이다. 재시도는 검증 실패 시 correction 1회뿐이다(`src/main/java/com/planb/ai/client/OpenAiClient.java:195`).
- `MissingSlotCompleter.addMealSlots`는 같은 판정을 그 시점의 `schedules`로 다시 돌려(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:212`) 누락분만 채운다. 빈 목록이면 루프가 한 번도 돌지 않으므로 아무 슬롯도 추가하지 않는다(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:221`). 채워 넣는 시작시각은 `MealSlotPolicy.configuredMealTime`이고(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:222`), 대표메뉴를 확인한 후보가 없으면 로그만 남기고 그 날 식사 보정을 중단한다(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:236`).
- `PlanService.validateMealSlots`는 누락이 하나라도 있으면 `"식사 슬롯 누락: day=<n>, meals=[...]"` 사유로 `PLAN.EXCEPTION.INVALID_AI_PLACE`를 던진다(`src/main/java/com/planb/domain/travel/service/PlanService.java:616`). 빈 목록이면 통과한다. 즉 이 검증은 "요구되는데 없는 것"만 막고, "하루가 짧아서 요구되지 않은 것"은 정상으로 통과시킨다.
- 편집 경로에서는 편집 전 일정에 이미 없던 식사를 `baselineMissingMeals`로 모아 면제한다(`src/main/java/com/planb/domain/travel/service/PlanService.java:637`). 면제 기준은 요구 여부가 아니라 슬롯의 존재 여부다(`src/main/java/com/planb/domain/travel/service/PlanService.java:656`). 생성 경로는 `currentPlan`이 null이라 면제가 없다(`src/main/java/com/planb/domain/travel/service/PlanService.java:129`).
- `PlanService.missingMealDays`는 재보정이 필요한지만 판단한다(`src/main/java/com/planb/domain/travel/service/PlanService.java:673`). 빈 목록이면 `refillMissingMeals`가 즉시 원본을 돌려주고 두 번째 보정·정규화를 건너뛴다(`src/main/java/com/planb/domain/travel/service/PlanService.java:565`).

## 하루의 시작·종료 시각을 정하는 것

하루 길이는 어디에서도 직접 지정되지 않는다. 슬롯들의 시각에서 파생된 값일 뿐이다. 다음 단계들이 그 값을 바꾼다.

1. `MissingSlotCompleter.complete`가 부족한 관광 슬롯과 식사 슬롯을 채운다(`src/main/java/com/planb/domain/travel/service/PlanService.java:743`). 채워 넣는 슬롯의 체류시간은 `FILLED_STAY_MINUTES = 60`(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:39`), 앞뒤 최소 간격은 `FILLED_GAP_MINUTES = 20`(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:42`)이다. 하루에 슬롯이 하나도 없을 때만 `DEFAULT_DAY_START = LocalTime.of(9, 0)`을 시작점으로 쓴다(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:44`, `src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:466`). 이 상수는 "하루는 9시에 시작한다"는 규칙이 아니라 빈 하루의 대체값이다.
2. `TouristPlaceCountPolicy.trimExcess`가 기준 개수를 넘는 관광 슬롯을 제거한다(`src/main/java/com/planb/domain/travel/service/PlanService.java:751`). 기준은 동행인 중 한 명이라도 `WalkType.MINIMAL`이면 `MINIMAL_WALK_COUNT = 2`, 그 외에는 `DEFAULT_COUNT = 3`이다(`src/main/java/com/planb/domain/travel/policy/TouristPlaceCountPolicy.java:22`, `src/main/java/com/planb/domain/travel/policy/TouristPlaceCountPolicy.java:24`, `src/main/java/com/planb/domain/travel/policy/TouristPlaceCountPolicy.java:45`). 제거는 `MUST_HAVE`를 남기고 **뒤쪽 `ATTRACTION`부터** 한다(`src/main/java/com/planb/domain/travel/policy/TouristPlaceCountPolicy.java:113`). 이것이 하루의 종료시각을 직접 앞당기는 유일한 지점이다. 제거 후 남은 슬롯의 `travelMinutes`는 그대로 두며, 이는 파일에 `ponytail:` 주석으로 명시되어 있다(`src/main/java/com/planb/domain/travel/policy/TouristPlaceCountPolicy.java:88`).
3. `ScheduleNormalizer.normalizeScheduleTimes`가 시각을 확정한다(`src/main/java/com/planb/domain/travel/service/PlanService.java:761`). 종료시각은 항상 `startTime + stayMinutes`로 다시 계산한다(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:147`). `stayMinutes`가 없거나 0 이하이거나 장소가 필요 없는 슬롯은 손대지 않고 그대로 통과시킨다(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:84`).
4. `PlanService.fillMissingTravelMinutes`는 시각을 바꾸지 않는다. 비어 있거나 0인 `travelMinutes`만 카카오 경로 조회로 채우고(`src/main/java/com/planb/domain/travel/service/PlanService.java:1468`), 조회 실패 시 원래 값을 유지한다(`src/main/java/com/planb/domain/travel/service/PlanService.java:1482`). 다만 여기서 채운 `travelMinutes`가 뒤이은 정규화의 `earliestStart` 계산에 쓰이므로(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:103`) 간접적으로 하루를 뒤로 민다.

정규화가 식사 슬롯에 적용하는 허용 창은 등록 식사시각 ±`MEAL_TIME_TOLERANCE_MINUTES = 30`분이다(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:44`, `src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:189`). 여러 동행인의 창이 교차하지 않으면 `"여행자 식사시간 허용 범위 불일치"`로 실패한다(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:201`). 식사가 창보다 늦어지면 `tryShiftPreviousPlaces`가 직전 식사 이후의 장소들을 통째로 앞당긴다(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:116`, `src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:213`). **이 이동은 이미 있는 식사 슬롯을 맞추기 위한 것이며, 없는 식사를 만들지는 않는다.** 앞당길 수 없으면 실패시키지 않고 가능한 가장 이른 시각에 둔다(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:114`). `DEFAULT_MEAL_MINUTES = 60`은 종료시각 없는 식사의 복약 기준을 만들 때만 쓰인다(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:47`, `src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:505`).

순서가 중요하다. `validatePlaces` 안에서는 식사 보정(`:743`)이 관광 초과분 제거(`:751`)와 정규화(`:761`)보다 **앞**이다. 즉 보정 시점의 하루는 제거 전의 긴 하루이고, 최종 판정 시점의 하루는 제거·정규화 후의 짧아진 하루다. `finishPlan`은 확정된 시각으로 `refillMissingMeals`를 한 번 더 돌려 이 어긋남을 좁히지만(`src/main/java/com/planb/domain/travel/service/PlanService.java:517`), 재보정은 의도적으로 한 번만 한다(`src/main/java/com/planb/domain/travel/service/PlanService.java:555` 주석). 그리고 이 재보정도 하루가 **넓어졌을** 때만 새 식사를 발견한다. 제거로 하루가 **좁아진** 경우에는 요구가 사라지므로 `missingMealDays`가 비고 재보정 자체가 실행되지 않는다.

## 하루 길이를 보장하는 규칙은 없다

찾지 못했다. 다음을 확인했고, 어느 것도 하루가 등록 식사시각까지 닿도록 요구하거나 짧은 하루를 늘리지 않는다.

- 하루 전체의 시작·종료 시각을 규정하는 상수는 존재하지 않는다. `MissingSlotCompleter.DEFAULT_DAY_START`(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:44`)는 빈 하루의 대체값이고, `PlanPlaceResolver`의 `LocalTime.of(11, 0)` / `LocalTime.of(16, 0)`(`src/main/java/com/planb/domain/travel/helper/PlanPlaceResolver.java:306`, `src/main/java/com/planb/domain/travel/helper/PlanPlaceResolver.java:310`)은 이미 있는 음식점 슬롯의 `scheduleType`을 시각으로 되맞추는 구간이지 하루의 경계가 아니다.
- 날짜 단위 검증은 일차 번호와 날짜 일치만 본다(`src/main/java/com/planb/domain/travel/service/PlanService.java:838`). 하루의 길이나 슬롯 시각 범위는 검사하지 않는다.
- 하루 단위로 개수를 강제하는 것은 관광 장소뿐이다(`src/main/java/com/planb/domain/travel/service/PlanService.java:862`). 식사는 개수가 아니라 `MealSlotPolicy` 판정으로만 강제된다.
- "등록한 세 끼가 여행 전체에서 최소 몇 번 나와야 한다" 같은 여행 단위 검증은 없다. `MealSlotPolicy`는 하루 단위로만 호출된다(`src/main/java/com/planb/domain/travel/service/PlanService.java:608`, `src/main/java/com/planb/ai/handler/TravelRecommendHandler.java:277`).

결과적으로 하루가 09:00–16:18이면 `BREAKFAST`(08:00)와 `DINNER`(18:00)는 양쪽 다 구간 밖이라 요구되지 않고, 채우는 쪽도 거부하는 쪽도 재시도하는 쪽도 아무 일을 하지 않는다. 이는 `MealSlotPolicy` 주석이 밝힌 의도 그대로다 — 만들 수 없는 일정을 요구하면 재시도가 끝없이 실패하기 때문이다(`src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:15`).

## 프롬프트가 요구하는 것

`src/main/java/com/planb/ai/prompt/` 전체를 읽었다. 하루의 시작·종료 시각을 지정하는 문장은 없다.

- 식사 개수는 명시적으로 열어 두었다. `"식사 슬롯 수는 고정하지 않습니다."` / `"실제 음식점과 메뉴 후보를 Tool로 확인할 수 있는 경우에만 식사 슬롯을 구성합니다."`(`src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:91`).
- 시각에 대한 지시는 순서와 예상 시간대 수준이다. `"startTime, endTime은 후보 일정의 순서와 예상 시간대를 표현합니다."` / `"이동시간, 체류시간, 식사시간을 반영한 최종 값은 Java가 정규화하고 검증합니다."`(`src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:356`).
- 체류시간만 범위를 준다. `"ATTRACTION 60~120분, RESTAURANT/LOCAL_FOOD 60~90분, CAFE_REST 30~60분,"`(`src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:360`).
- 등록 식사시각(`mealInfo`)은 직렬화 형식 설명에만 등장하며(`src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:335`), "이 시각에 식사를 배치하라"는 지시는 없다. `MATCH_MEAL_TIME` 여행 스타일도 `"실제 음식점과 메뉴 후보를 우선 확보합니다."`라고만 말한다(`src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:99`).
- 날짜 개수만 정확히 요구한다. `"DAY_TRIP은 1개, ONE_NIGHT_TWO_DAYS는 2개, TWO_NIGHTS_THREE_DAYS는 3개입니다."`(`src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:89`).
- 마지막 날을 다르게 다루라는 지시는 없다. `"마지막 날짜"`라는 표현은 전부 중복 금지 범위를 `"1일차부터 마지막 날짜까지"`로 넓히는 맥락뿐이다(`src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:117`, `src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:144`, `src/main/java/com/planb/ai/prompt/TravelPlanPrompt.java:480`).
- `RebuildPlanDayPrompt`도 하루 하나를 재구성하면서 시각 규칙은 `"startTime/endTime과 stayMinutes를 일치시킵니다."` 정도만 둔다(`src/main/java/com/planb/ai/prompt/RebuildPlanDayPrompt.java:43`). 식사 개수 요구는 없다.

## 마지막 날 처리

특별 취급이 없다. `DateType`은 `plusDays`로 종료일만 계산하고(`src/main/java/com/planb/domain/travel/entity/constant/DateType.java:33`), `ONE_NIGHT_TWO_DAYS`는 `plusDays = 1`이다(`src/main/java/com/planb/domain/travel/entity/constant/DateType.java:17`). 이 값은 두 곳에서만 쓰인다.

- 응답의 날짜 개수 검증: `dateType.getPlusDays() + 1`(`src/main/java/com/planb/ai/handler/TravelRecommendHandler.java:411`, `src/main/java/com/planb/domain/travel/service/PlanService.java:844`).
- 체크아웃, 귀가, 마지막 날 단축 같은 개념은 코드에도 프롬프트에도 없다. 2일차는 1일차와 완전히 동일한 규칙으로 검증된다.

즉 마지막 날이 짧아지는 것은 규칙이 허용해서가 아니라 규칙이 하루 길이를 아예 보지 않기 때문이다.

## 현재 동작을 고정한 테스트

`src/test/java/com/planb/unit/domain/travel/policy/MealSlotPolicyTest.java`가 판정의 사양이다. 임시 프로브 테스트는 현재 파일에 남아 있지 않다.

- `spanningConfiguredMealWithoutSlotIsMissing`: 09:40–14:10 하루에 12:00 점심이 들어오면 `LUNCH` 누락(`src/test/java/com/planb/unit/domain/travel/policy/MealSlotPolicyTest.java:23`).
- `spanningConfiguredMealWithSlotIsSatisfied`: 같은 하루에 12:00 식사 슬롯이 있으면 누락 없음(`src/test/java/com/planb/unit/domain/travel/policy/MealSlotPolicyTest.java:46`).
- `dayEndingBeforeConfiguredMealRequiresNothing`: **09:00–11:00 하루는 아무 식사도 요구하지 않는다**(`src/test/java/com/planb/unit/domain/travel/policy/MealSlotPolicyTest.java:73`). 보고된 현상을 그대로 고정한 테스트다. 기준을 바꾸면 이 테스트가 깨진다.
- `mealInfoNotAppliedRequiresNothing`: `applied=false`면 끼니별 플래그가 켜져 있어도 요구하지 않음(`src/test/java/com/planb/unit/domain/travel/policy/MealSlotPolicyTest.java:92`).
- `spanningTwoConfiguredMealsRequiresBoth`: 07:30–14:10 하루는 `BREAKFAST`와 `LUNCH`를 모두 요구(`src/test/java/com/planb/unit/domain/travel/policy/MealSlotPolicyTest.java:128`).

주변 테스트도 같은 기준을 전제한다.

- `TravelRecommendHandlerContractTest`: `"후보로 채울 수 있는 식사 슬롯 누락은 재시도 대상 아님"`(`src/test/java/com/planb/unit/ai/handler/TravelRecommendHandlerContractTest.java:144`), `"후보로 채울 수 없는 식사 슬롯 누락만 교정 사유로 전달"`(`src/test/java/com/planb/unit/ai/handler/TravelRecommendHandlerContractTest.java:167`).
- `MissingSlotCompleterTest`: `"빠진 식사 슬롯을 등록 식사시각에 실제 메뉴명으로 추가"`(`src/test/java/com/planb/unit/ai/handler/MissingSlotCompleterTest.java:150`), `"후보의 대표메뉴가 전부 이미 쓰였으면 식사 슬롯을 채우지 않음"`(`src/test/java/com/planb/unit/ai/handler/MissingSlotCompleterTest.java:277`).
- `PlanPlaceValidationTest`: `"정규화가 일정을 앞당겨 식사시각을 지나게 되면 그 식사 슬롯이 채워짐"`(`src/test/java/com/planb/unit/domain/travel/service/PlanPlaceValidationTest.java:2613`)은 하루가 **넓어지는** 방향만 검증한다. 좁아지는 방향을 검증하는 테스트는 찾지 못했다. `"채울 음식점 후보가 없으면 식사 슬롯 누락으로 거부"`(`src/test/java/com/planb/unit/domain/travel/service/PlanPlaceValidationTest.java:2697`)는 요구가 살아 있는 경우의 거부만 본다.
- `ScheduleNormalizerTest`: `"이동시간으로 식사시간을 맞출 수 없는 일정의 예외 없는 최선 배치"`(`src/test/java/com/planb/unit/domain/travel/service/ScheduleNormalizerTest.java:88`), `"식사 슬롯 없는 날의 설정 식사시간 기준 식후 복약 배치"`(`src/test/java/com/planb/unit/domain/travel/service/ScheduleNormalizerTest.java:159`). 후자는 식사 슬롯이 없는 하루를 정상 입력으로 전제한다.

이번 조사에서는 코드를 읽기만 했고 테스트를 실행하지 않았다.
