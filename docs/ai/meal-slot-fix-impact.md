# 식사 슬롯 수정 방향의 영향 범위

> 상태: 과거 영향 분석 스냅샷
> 아래 호출 흐름과 결론은 작성 당시 코드를 기준으로 한다. 현재 식사 요구·면제·최종 거부
> 계약은 `docs/ai/meal-slot-policy.md`, `MealSlotPolicy`와 `PlanService`에서 확인한다.

`docs/ai/meal-slot-policy.md`가 남긴 네 가지 미확인 지점을 코드로 확인한 결과다. 기존 문서가 이미 다룬 판정 규칙과 `validatePlaces` 내부 순서는 반복하지 않는다. 테스트는 이번에도 실행하지 않았고, 전부 코드를 읽어 추론한 결과다.

## 1. 하루 밖 시각에 식사 슬롯을 넣을 수 있는가 — 넣을 수 있고, 되돌리는 장치는 없다

`addMealSlots`는 `MealSlotPolicy.configuredMealTime`이 돌려준 시각을 그대로 시작시각으로 쓴다(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:222`). 그 시각이 지금 하루의 종료시각보다 늦든 시작시각보다 이르든 검사하지 않는다. 만들어진 슬롯의 종료시각은 `startTime + FILLED_STAY_MINUTES`이고(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:430`), 이동시간은 `null`로 둔다(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:439`).

넣는 위치는 목록 끝이지만(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:249`), `fillDay`가 반환 직전에 `startTime` 기준으로 전체를 다시 정렬한다(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:138`). 따라서 하루 시작보다 이른 아침 슬롯은 첫 번째 자리로, 하루 종료보다 늦은 저녁 슬롯은 마지막 자리로 간다. 목록 순서가 어긋난 채 정규화에 들어가지는 않는다.

이어지는 `ScheduleNormalizer.normalizeScheduleTimes`는 이 슬롯을 앞당기지도 밀지도 않는다. `earliestStart`는 `travelMinutes`가 `null`이 아닐 때만 계산되는데(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:100`), 방금 채운 슬롯의 `travelMinutes`는 `null`이다. `validatePlaces`는 정규화(`src/main/java/com/planb/domain/travel/service/PlanService.java:761`) 전에 `fillMissingTravelMinutes`를 돌리지 않으므로 이 시점의 `travelMinutes`는 여전히 `null`이다. 남는 것은 식사시간 창 보정뿐인데, 창은 등록 식사시각 ±30분이고(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:134`, `src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:138`) 채워 넣은 시각은 정확히 그 중심이라 어느 쪽으로도 움직이지 않는다. 종료시각은 `startTime + stayMinutes`로 다시 계산되어(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:147`) 하루가 그만큼 길어진 채 확정된다.

`finishPlan`의 두 번째 경로에서는 `fillMissingTravelMinutes`가 `null` 이동시간을 채우고(`src/main/java/com/planb/domain/travel/service/PlanService.java:1468`) 정규화가 다시 돈다(`src/main/java/com/planb/domain/travel/service/PlanService.java:510`). 이때도 되돌아오지 않는다. 늦은 저녁 슬롯이면 `earliestStart = 직전 종료 + 이동시간`이 식사시각보다 이르므로 `earliestStart.isAfter(startTime)`이 거짓이고(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:127`) 시각이 유지된다. 반대로 이동시간이 커서 창 상한을 넘으면 `tryShiftPreviousPlaces`가 앞 장소들을 당기고(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:116`), 당기지 못해도 실패시키지 않고 더 늦은 시각에 둔다(`src/main/java/com/planb/domain/travel/service/ScheduleNormalizer.java:143`). 어느 분기도 하루를 원래 종료시각으로 되돌리지 않는다.

**결론: 하루는 등록 식사시각까지 실제로 늘어난다.** 되감는 장치는 찾지 못했다. 다만 확인한 한계가 하나 있다. 아침 슬롯이 정렬로 맨 앞에 오면 그 뒤 슬롯의 `travelMinutes`는 이전 첫 장소 기준 값 그대로 남는다. `fillScheduleTravelMinutes`는 값이 비었거나 0일 때만 다시 조회하기 때문이다(`src/main/java/com/planb/domain/travel/service/PlanService.java:1468`). 이 값이 실제 경로와 어긋나도 재조회되지 않는다는 점은 코드로 확인했으나, 그것이 최종 시간표에 얼마나 어긋난 값을 남기는지는 실행 없이 확인하지 못했다.

## 2. `PLAN.EXCEPTION.INVALID_AI_PLACE`가 사용자에게 닿는 경로

`validateMealSlots`는 `finishPlan` 안에서 호출된다(`src/main/java/com/planb/domain/travel/service/PlanService.java:524`). `finishPlan`은 `makePlanByAi`(`src/main/java/com/planb/domain/travel/service/PlanService.java:129`)와 `makeEditPlanByAi`(`src/main/java/com/planb/domain/travel/service/PlanService.java:193`)에서 **try 블록 바깥**에 있다. 두 메서드의 try는 영양평가 수집을 닫기 위한 `finally`만 달고 있어(`src/main/java/com/planb/domain/travel/service/PlanService.java:125`, `src/main/java/com/planb/domain/travel/service/PlanService.java:174`) 예외를 잡지 않는다.

애플리케이션 코드에서 `INVALID_AI_PLACE`를 구분해 잡는 곳은 한 군데뿐이다. `ensureDaysRebuilt`가 `validatePlaces` 호출을 감싸고, 이 코드일 때만 사유로 바꿔 재구성을 한 번 더 돌린다(`src/main/java/com/planb/domain/travel/service/PlanService.java:282`, `src/main/java/com/planb/domain/travel/service/PlanService.java:283`, `src/main/java/com/planb/domain/travel/service/PlanService.java:295`). 반복은 `attempt < 2`로 묶여 있다(`src/main/java/com/planb/domain/travel/service/PlanService.java:237`). **그러나 이 catch가 감싸는 것은 `validatePlaces`이고 `validateMealSlots`는 그 안에 없다.** 즉 식사 슬롯 누락은 이 재시도를 타지 않는다.

따라서 두 갈래로 나뉜다.

- **생성 경로(HTTP)**: `TravelFacade.makeTravelOptionsAndRecommend`는 `@Transactional`만 붙어 있고 catch가 없다(`src/main/java/com/planb/domain/travel/facade/TravelFacade.java:142`, `src/main/java/com/planb/domain/travel/facade/TravelFacade.java:193`). 예외가 그대로 `TravelController.addTravelOptionsAndRecommend`(`src/main/java/com/planb/domain/travel/controller/TravelController.java:120`)를 지나 `ApiExceptionHandler.baseExceptionHandler`에 닿는다(`src/main/java/com/planb/global/config/exception/ApiExceptionHandler.java:43`). 이 핸들러에는 `@ResponseStatus`가 없다. 같은 파일의 다른 핸들러들은 명시적으로 붙여 두었으므로(`src/main/java/com/planb/global/config/exception/ApiExceptionHandler.java:113`, `src/main/java/com/planb/global/config/exception/ApiExceptionHandler.java:143`) 누락이 아니라 선택이다. 결과는 **HTTP 200 + `success:false` 본문**이고 `errorCode`는 `PLAN.EXCEPTION.INVALID_AI_PLACE`, 메시지는 `"일정 장소 검증 실패: 식사 슬롯 누락: day=<n>, meals=[...]"`다(`src/main/java/com/planb/global/config/exception/PlanEditExceptionEnum.java:11`, `src/main/java/com/planb/domain/travel/service/PlanService.java:618`). 트랜잭션은 롤백되어 여행 자체가 저장되지 않는다. 이 동작은 `src/test/java/com/planb/integration/domain/travel/TravelValidationIntegrationTest.java:687`이 고정하고 있다. 컨트롤러 문서에도 이 코드가 명시되어 있다(`src/main/java/com/planb/domain/travel/controller/TravelController.java:112`).
- **편집 경로(WebSocket)**: `makeEditPlanByAi`는 `TravelFacade.makeEditPlanPreview`(`src/main/java/com/planb/domain/travel/facade/TravelFacade.java:496`)를 거쳐 `ChatMessageFacade.handleTalk`(`src/main/java/com/planb/domain/chat/facade/ChatMessageFacade.java:115`)로 올라간다. 두 곳 모두 catch가 없다. 마지막이 `ChatController.sendMessage`의 `catch (Exception e)`다(`src/main/java/com/planb/domain/chat/controller/ChatController.java:39`). 여기서 사유는 로그로만 남고(`src/main/java/com/planb/domain/chat/controller/ChatController.java:43`), 사용자에게는 `publishEditFailedReply`가 고정 문구 하나를 발행한다(`src/main/java/com/planb/domain/chat/controller/ChatController.java:50`, `src/main/java/com/planb/domain/chat/facade/ChatFacade.java:324`). 그 문구는 `"일정 수정 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요."`다(`src/main/java/com/planb/domain/chat/helper/ChatAiReplyMessageHelper.java:26`). 에러 코드도 어느 식사가 빠졌는지도 전달되지 않는다. 주석이 이유를 밝혀 두었다 — STOMP에는 요청에 대응하는 응답 자리가 없다(`src/main/java/com/planb/domain/chat/controller/ChatController.java:41`). `src/test/java/com/planb/integration/domain/chat/ChatAiEditPlanIntegrationTest.java:437`이 이 경로를 고정한다.

정리하면 **재시도는 없고, 생성은 HTTP 200 실패 본문, 편집은 일반 실패 안내 한 줄**이다. 조용한 대체(fallback)는 없다. 다만 편집 경로의 안내 문구는 어떤 실패든 동일하므로 사용자 관점에서는 원인이 구분되지 않는다.

## 3. "재시도가 끝없이 실패한다"는 근거가 아직 유효한가 — 이미 완화되었다

`MealSlotPolicy`의 클래스 주석이 근거로 든 문장은 `src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:16`이다. 현재 코드에서 무한 반복은 성립하지 않는다.

- `OpenAiClient.call`은 한 호출당 모델을 최대 두 번 부른다. 검증이 실패하면 correction 요청으로 `callAndValidate`를 **한 번** 부르고(`src/main/java/com/planb/ai/client/OpenAiClient.java:199`), 그 안에서 또 실패하면 재귀하지 않고 `AiOrchestrationException`을 던진다(`src/main/java/com/planb/ai/client/OpenAiClient.java:245`). 같은 응답이 반복되면 더 일찍 끊는다(`src/main/java/com/planb/ai/client/OpenAiClient.java:233`). 파싱 실패 경로도 재시도 한 번뿐이다(`src/main/java/com/planb/ai/client/OpenAiClient.java:169`). 루프도 카운터도 없이 구조적으로 2회에 묶여 있다.
- 검증 사유가 correction까지 가기 전에 `unfillable`이 이번 호출의 미사용 음식점 후보 수만큼 덜어낸다(`src/main/java/com/planb/ai/handler/TravelRecommendHandler.java:257`, `src/main/java/com/planb/ai/handler/TravelRecommendHandler.java:293`). 사유 하나가 슬롯 하나에 대응하므로, Java가 채울 수 있는 만큼은 AI에게 전달되지 않는다. 후보가 충분하면 correction 요청 자체가 일어나지 않는다. `src/test/java/com/planb/unit/ai/handler/TravelRecommendHandlerContractTest.java:145`와 `src/test/java/com/planb/unit/ai/handler/TravelRecommendHandlerContractTest.java:168`이 이 경계를 고정한다.
- 날짜 재구성 재시도도 `attempt < 2`로 묶여 있고(`src/main/java/com/planb/domain/travel/service/PlanService.java:237`), 식사 재보정은 의도적으로 한 번만 한다(`src/main/java/com/planb/domain/travel/service/PlanService.java:555`).

**결론: 끝없는 재시도는 더 이상 실제 위험이 아니다.** 요구를 늘려도 추가 비용의 상한은 호출당 AI 한 번이다. 다만 위험이 사라진 것이 아니라 형태가 바뀌었다. 요구를 만족시킬 후보가 없으면 재시도가 아니라 `validateMealSlots`가 최종 거부를 던지고(`src/main/java/com/planb/domain/travel/service/PlanService.java:616`) 2번에서 정리한 실패로 끝난다. 즉 "끝없는 재시도" 대신 "생성 자체의 실패"가 비용이다. 주석이 말한 근거는 현재 코드에 대해 더 이상 정확하지 않으므로 고칠 때 함께 갱신해야 한다.

## 4. 세 가지 수정 방향별로 깨지는 테스트

아래는 전부 코드를 읽어 판단한 것이고 실행으로 확인하지 않았다. 판단 근거가 되는 시각은 각 테스트의 헬퍼에서 확인했다.

### 공통 전제

- `TravelHealthContext.MealInfoContext`의 3인자 생성자는 `applied`와 끼니별 플래그를 모두 true로 만든다(`src/main/java/com/planb/ai/context/TravelHealthContext.java:68`). 테스트가 이 생성자를 쓰면 세 끼가 모두 등록된 상태다.
- `TouristPlaceCountPolicy.trimExcess`는 이미 `healthContexts`를 받는다(`src/main/java/com/planb/domain/travel/policy/TouristPlaceCountPolicy.java:60`). 방향 b는 시그니처 변경이 필요 없다.
- `MealSlotPolicy.missingMeals`는 하루 하나를 받는다(`src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:38`). 방향 c는 시그니처를 바꿔야 하므로 호출부 전부가 **컴파일 단계에서** 깨진다: `src/main/java/com/planb/domain/travel/service/PlanService.java:608`, `src/main/java/com/planb/domain/travel/service/PlanService.java:681`, `src/main/java/com/planb/ai/handler/TravelRecommendHandler.java:281`, `src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:212`, 그리고 테스트 쪽 `src/test/java/com/planb/unit/domain/travel/policy/MealSlotPolicyTest.java`의 다섯 호출과 `src/test/java/com/planb/unit/domain/travel/service/PlanPlaceValidationTest.java:2689`.

### a. 식사 보정을 `trimExcess`·정규화 **뒤로** 옮기는 경우

보정과 초과분 제거는 지금 `MissingSlotCompleter.complete` 하나로 묶여 있다(`src/main/java/com/planb/domain/travel/service/PlanService.java:743`). 관광 보정과 식사 보정이 같은 메서드 안에 있으므로(`src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:134`, `src/main/java/com/planb/ai/handler/MissingSlotCompleter.java:136`) **식사 보정만** 옮기려면 둘을 분리해야 한다. 이 구분이 영향 범위를 가른다.

식사 보정만 분리해 옮긴 경우, 깨지는 테스트를 찾지 못했다.

- `src/test/java/com/planb/unit/domain/travel/service/PlanPlaceValidationTest.java:2613` `"정규화가 일정을 앞당겨 식사시각을 지나게 되면 그 식사 슬롯이 채워짐"` — 살아남는다. 정규화 뒤로 옮기면 `validatePlaces` 단계에서 이미 아침이 요구 대상이 되어 `finishPlan`의 재보정을 기다리지 않고 채워진다. 단언은 최종 `missingMeals`가 비어 있는지만 본다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanPlaceValidationTest.java:2697` `"채울 음식점 후보가 없으면 식사 슬롯 누락으로 거부"` — 살아남는다. 후보가 없다는 사실은 순서와 무관하다.
- `src/test/java/com/planb/integration/domain/travel/TravelValidationIntegrationTest.java:687` `"채울 음식점 후보가 없는 식사 누락은 생성 실패와 DB 롤백"` — 살아남는다. 동행인이 `WalkType.MODERATE`라 기준 개수가 3이고(`src/test/java/com/planb/integration/domain/travel/TravelApiTestSupport.java:180`) `dayWithoutDinner`의 `ATTRACTION`도 3개라(`src/test/java/com/planb/integration/domain/travel/TravelValidationIntegrationTest.java:772`) 애초에 제거가 일어나지 않는다.
- `src/test/java/com/planb/integration/domain/travel/TravelValidationIntegrationTest.java:710` `"보존 편집에서 재구성 날짜의 식사 누락을 편집 호출 후보로 채움"` — 살아남는다. 같은 이유로 제거가 없고, 저녁 후보가 하나 등록되어 있다.
- `src/test/java/com/planb/unit/ai/handler/MissingSlotCompleterTest.java` 전체 — `MissingSlotCompleter`를 직접 부르므로 `PlanService`의 호출 순서와 무관하다. 전부 살아남는다.
- `src/test/java/com/planb/unit/domain/travel/service/ScheduleNormalizerTest.java` 전체 — `ScheduleNormalizer`만 직접 부른다. 전부 살아남는다.

`complete` 전체를 뒤로 옮기면 하나가 깨진다.

- `src/test/java/com/planb/unit/domain/travel/service/PlanPlaceValidationTest.java:2845` `"수정 응답의 부족한 관광 슬롯을 검증 전에 후보로 채움"` — 깨진다. 관광 슬롯 1개짜리 하루를 3개로 채우는 것을 확인하는데, 보정이 `validateTouristPlaceCounts`(`src/main/java/com/planb/domain/travel/service/PlanService.java:756`) 뒤로 가면 개수 검증이 먼저 거부한다.
- 같은 이유로 `src/test/java/com/planb/unit/domain/travel/service/PlanPlaceValidationTest.java:2807` `"수정 응답의 관광 장소 초과분을 검증 전에 제거"`는 살아남는다. 제거 대상은 AI가 준 4개 그대로이고 기대값 `["해운대", "이기대", "오죽헌"]`이 바뀌지 않는다.

### b. 등록 식사시각을 지나는 하루에서 `trimExcess`가 제거를 거부하는 경우

이 방향으로 깨지는 테스트를 하나도 찾지 못했다. 제거와 식사시각의 상호작용을 고정한 테스트가 없다.

- `src/test/java/com/planb/unit/domain/travel/policy/TouristPlaceCountPolicyTest.java:55` `"관광 장소 초과분을 뒤에서부터 제거해 기준 개수에 맞춤"` — 살아남는다. 이 파일의 `slot` 헬퍼는 모든 슬롯을 09:00~10:00으로 만들고(`src/test/java/com/planb/unit/domain/travel/policy/TouristPlaceCountPolicyTest.java:177`) 등록 식사시각은 08:00/12:00/18:00이라(`src/test/java/com/planb/unit/domain/travel/policy/TouristPlaceCountPolicyTest.java:200`) 하루 구간 안에 드는 식사가 애초에 없다. 보호할 대상이 없으므로 제거가 그대로 일어난다.
- `src/test/java/com/planb/unit/domain/travel/policy/TouristPlaceCountPolicyTest.java:71` `"초과분 제거 대상에서 사용자가 지정한 MUST_HAVE 슬롯 제외"` — 같은 이유로 살아남는다.
- `src/test/java/com/planb/unit/domain/travel/policy/TouristPlaceCountPolicyTest.java:87`, `:101`, `:117` — 제거가 일어나지 않는 경우들이라 영향 없다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanPlaceValidationTest.java:2807` `"수정 응답의 관광 장소 초과분을 검증 전에 제거"` — 살아남는다. 슬롯은 09/11/13/15시이고 하루는 16:00에 끝나는데, 이 테스트가 쓰는 동행인의 등록 식사시각은 05:00/05:30/05:45다(`src/test/java/com/planb/unit/domain/travel/service/PlanPlaceValidationTest.java:3199`). 하루 구간 안에 드는 식사가 없어 제거가 막히지 않는다.
- `src/test/java/com/planb/integration/domain/travel/TravelValidationIntegrationTest.java:687`, `:710` — 앞서 적은 대로 제거 자체가 일어나지 않는다.

**세 방향 중 기존 테스트를 가장 적게 건드린다.** 다만 이는 방향 b가 안전하다는 뜻이 아니라, 이 상호작용을 고정한 테스트가 없다는 뜻이다. 고칠 때 새 테스트가 필요하다.

### c. 하루가 아니라 여행 전체 기준으로 식사를 요구하는 경우

컴파일 단계 영향은 위 "공통 전제"에 적었다. 동작으로 깨지는 것은 다음이다.

- `src/test/java/com/planb/unit/domain/travel/policy/MealSlotPolicyTest.java:73` `"등록 식사시각을 지나지 않는 하루는 식사 슬롯을 요구하지 않음"` — **정면으로 깨진다.** 이 테스트가 곧 바꾸려는 규칙이다.
- `src/test/java/com/planb/unit/domain/travel/policy/MealSlotPolicyTest.java:23`, `:46`, `:128` — 판정 결과가 같으므로 살아남는다(시그니처 수정은 필요).
- `src/test/java/com/planb/unit/domain/travel/policy/MealSlotPolicyTest.java:92` `"식사시간 미적용 동행인만 있으면 식사 슬롯을 요구하지 않음"` — 살아남는다. `applied=false`는 여행 단위로 봐도 요구가 없다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java:490` `"ACTIVE 여행자의 모든 날짜 관광 장소 3개 일정 허용"` — 깨진다. `attraction` 헬퍼가 만드는 하루는 09:00~10:30뿐이라(`src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java:1706`) 지금은 어떤 식사도 요구되지 않는다. 여행 단위로 보면 세 끼가 모두 요구되는데 `travelRecommendHandler`가 mock이라 음식점 후보가 하나도 기록되지 않아 보정이 실패하고 `validateMealSlots`가 거부한다. 단언은 하루 슬롯이 3개인지를 본다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java:617` `"MUST_HAVE의 날짜별 관광 장소 개수 포함"` — 깨진다. 하루가 09:00~13:00이라 점심만 있고 아침·저녁이 새로 요구되는데 채울 후보가 없다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java:687` `"MEDICATION 슬롯의 MEDICATION_SCHEDULE 태그 자동 부여"` — 깨진다. `withRequiredSlots`가 만드는 하루가 09:00~13:00이고(`src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java:1674`) 점심만 들어 있다. 아침·저녁이 새로 요구되고 후보가 없다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java:755` `"WITH_MEAL/AFTER_MEAL 복약 규칙의 실제 식사시간 기준 복약 시각 재계산"` — 같은 이유로 깨진다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java:896` `"동일 시각의 여러 복약 일정 하나로 병합"` — 같은 이유로 깨진다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java:1260` `"여행자 중 알레르기/기피 음식 보유 시 RESTAURANT 슬롯의 ALLERGY_CHECK 태그 자동 부여"` — 같은 이유로 깨진다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java:427`, `:552` — 살아남는다. 관광 장소 개수 검증(`src/main/java/com/planb/domain/travel/service/PlanService.java:756`)이 식사 검증보다 먼저 거부하고, 단언은 에러 코드만 본다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java:996`, `:1082` — 살아남는다. 둘 다 `INVALID_AI_PLACE` 코드만 단언하므로 거부 사유가 바뀌어도 통과한다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java`의 나머지 `makePlanByAi` 테스트(`:156`, `:221`, `:300`, `:364`, `:852`, `:1149`, `:1180`, `:1211`, `:1336`, `:1410`, `:1473`) — 살아남는다. `travelPlanContext()`가 동행인 없는 컨텍스트를 만들고(`src/test/java/com/planb/unit/domain/travel/service/PlanServiceTest.java:1630`) 동행인이 없으면 판정 자체가 없다.
- `src/test/java/com/planb/integration/domain/travel/TravelValidationIntegrationTest.java:710` `"보존 편집에서 재구성 날짜의 식사 누락을 편집 호출 후보로 채움"` — 깨진다. 하루가 09:00 시작이라 아침 08:00이 새로 요구되는데, 등록된 음식점 후보는 `"보정 음식점"` 하나뿐이다(`src/test/java/com/planb/integration/domain/travel/TravelValidationIntegrationTest.java:733`). `addMealSlots`는 `BREAKFAST → LUNCH → DINNER` 순으로 돌므로(`src/main/java/com/planb/domain/travel/policy/MealSlotPolicy.java:22`) 그 하나를 아침이 가져가고 저녁이 비게 되어 `contains("DINNER")` 단언이 깨진다. 편집 경로의 `baselineMissingMeals` 면제는 `validateMealSlots`에만 적용되고 보정기는 보지 않으므로 이 순서를 막지 못한다.
- `src/test/java/com/planb/integration/domain/travel/TravelValidationIntegrationTest.java:687` — 살아남는다. 어차피 거부되고 단언은 에러 코드만 본다.
- `src/test/java/com/planb/integration/domain/chat/ChatAiEditPlanIntegrationTest.java:348` `"기존 일정에 없던 식사는 편집이 하루를 앞당겨도 요구하지 않음"` — 살아남는다. 면제 기준이 요구 여부가 아니라 편집 전 슬롯의 존재 여부이기 때문이다(`src/main/java/com/planb/domain/travel/service/PlanService.java:656`).
- `src/test/java/com/planb/unit/ai/handler/TravelRecommendHandlerContractTest.java:145`, `:168` — 살아남는다. `attractions` 헬퍼가 3개일 때 하루가 09:00~12:00이라 점심 12:00이 이미 경계 안이고(`src/test/java/com/planb/unit/ai/handler/TravelRecommendHandlerContractTest.java:543`), 이 컨텍스트는 점심만 등록되어 있다(`src/test/java/com/planb/unit/ai/handler/TravelRecommendHandlerContractTest.java:215`).
- `src/test/java/com/planb/unit/ai/handler/MissingSlotCompleterTest.java` 전체 — 살아남는다. 이 파일의 동행인은 점심만 등록되어 있고(`src/test/java/com/planb/unit/ai/handler/MissingSlotCompleterTest.java:434`) 검사하는 하루들이 모두 12:00을 지난다. 다만 `:211` `"채울 후보가 없으면 일정을 그대로 둠"`은 하루가 09:00~10:00이라 판정이 달라지는데, 후보가 없어 아무것도 채워지지 않으므로 결과 크기 1이 그대로 유지된다.
- `src/test/java/com/planb/unit/domain/travel/service/ScheduleNormalizerTest.java` 전체 — 살아남는다. `MealSlotPolicy`를 거치지 않는다. 특히 `:160` `"식사 슬롯 없는 날의 설정 식사시간 기준 식후 복약 배치"`는 식사 슬롯 없는 하루를 정상 입력으로 두지만, `ScheduleNormalizer`를 직접 부르므로 요구 판정과 무관하다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanPlaceValidationTest.java:2807`, `:2845` — 살아남는다. 새로 요구되는 아침·저녁을 채우지 못해도, 편집 경로라 `baselineMissingMeals`가 편집 전 일정에 없던 식사를 면제한다(`src/main/java/com/planb/domain/travel/service/PlanService.java:637`). 이 두 테스트의 편집 전 일정은 관광 슬롯 하나뿐이다.
- `src/test/java/com/planb/unit/domain/travel/service/PlanPlaceValidationTest.java:2613`, `:2697` — 살아남는다. 두 하루 모두 이미 점심을 포함하고, 저녁 18:00이 새로 요구되더라도 `:2613`은 최종 `missingMeals`가 비어 있어야 한다는 단언이라 후보가 모자라면 깨질 수 있다. 여분 음식점 후보가 `"아침밀면"` 하나뿐이므로(`src/test/java/com/planb/unit/domain/travel/service/PlanPlaceValidationTest.java:2658`) **`:2613`은 깨질 가능성이 높다**. 확실히 하려면 실행이 필요한데 이번에는 확인하지 못했다.

`src/test/java/com/planb/integration/ai/handler/TravelRecommendHandlerTest.java`와 `src/test/java/com/planb/integration/ai/service/PlanEditRebuildTest.java`는 실제 OpenAI를 호출하는 테스트라 결과가 응답에 따라 달라진다. 세 방향 중 어느 것이 이 두 파일을 깨뜨리는지는 판단할 수 없었다.

## 확인하지 못한 것

- 어떤 테스트도 실행하지 않았다. 위 판정은 전부 읽기 기반이다.
- `PlanPlaceValidationTest:2613`이 방향 c에서 실제로 깨지는지는 후보 소진 순서에 달려 있어 단정하지 못했다.
- 실제 AI를 호출하는 두 통합 테스트(`TravelRecommendHandlerTest`, `PlanEditRebuildTest`)의 영향.
- 아침 슬롯이 하루 맨 앞으로 정렬될 때 뒤따르는 슬롯의 오래된 `travelMinutes`가 최종 시간표를 얼마나 어긋나게 하는지.
