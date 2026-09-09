package com.planb.domain.travel.service;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlanEditScope;
import com.planb.domain.travel.helper.PlanEditValidationHelper;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.prompt.PlaceReselectPrompt;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.dto.request.CreatePlanRequest;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.entity.constant.NutritionLevel;
import com.planb.domain.travel.entity.constant.NutritionType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.helper.PlanPlaceHelper.Validation;
import com.planb.domain.travel.helper.PlanPlaceHelper;
import com.planb.domain.travel.repository.PlanRepository;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    // 실제 근거가 있는 NutritionType만 RecommendationTag로 매핑 (평가는 되지만 대응 태그가 없는 타입 제외)
    private static final Map<NutritionType, RecommendationTag> NUTRITION_REFERENCE_TAGS = Map.of(
            NutritionType.CARBOHYDRATE, RecommendationTag.CARBOHYDRATE_REFERENCE,
            NutritionType.SODIUM, RecommendationTag.SODIUM_REFERENCE,
            NutritionType.SATURATED_FAT, RecommendationTag.SATURATED_FAT_REFERENCE
    );

    // 복약 일정 계산 시 하루 중 식사 슬롯을 찾기 위한 scheduleType 집합
    private static final Set<ScheduleType> MEAL_SCHEDULE_TYPES = Set.of(
            ScheduleType.BREAKFAST,
            ScheduleType.LUNCH,
            ScheduleType.DINNER
    );

    private static final long MEAL_TIME_TOLERANCE_MINUTES = 30;

    /*
    Repository
     */
    private final PlanRepository planRepository;

    /*
    Helper
     */
    private final PlanPlaceHelper planPlaceHelper;

    private final PlanEditValidationHelper planEditValidationHelper;

    /*
    Handler
     */
    private final TravelRecommendHandler travelRecommendHandler;

    // STEP 7에서 AI가 getRoute Tool 호출을 빠뜨려 travelMinutes가 비어 있는 슬롯을
    // Java에서 직접 채우기 위한 route 조회 (AI Tool인 TourismTool.getRoute와 동일한 호출)
    private final KakaoMapServiceHandler kakaoMapServiceHandler;

    /*
    Tool 호출 결과 수집기
     */
    private final NutritionEvaluationCollector nutritionEvaluationCollector;

    public Plan createPlan(CreatePlanRequest createPlanRequest){

        return Plan
                .builder()
                .planName(createPlanRequest
                        .planName())
                .travel(createPlanRequest
                        .travel())
                .build();
    }

    // AI 일정 생성 및 검색 원본 기반 슬롯 확정
    public CreatePlanAiResponse makePlanByAi(TravelPlanContext context) {

        nutritionEvaluationCollector.start();

        CreatePlanAiResponse validated;

        List<NutritionEvaluationCollector.FoodNutritionEvaluation> evaluations;

        try {
            PlaceCandidateContext candidates = new PlaceCandidateContext();

            CreatePlanAiResponse response = travelRecommendHandler.createPlanByAi(context, candidates);

            validated = validatePlaces(response, candidates, context, null);
        } finally {
            evaluations = nutritionEvaluationCollector.finish();
        }

        return finishPlan(validated, context, evaluations);
    }

    // AI 일정 수정 및 검증된 기존 슬롯 복구
    public EditPlanAiResponse makeEditPlanByAi(PlanEditContext context) {

        nutritionEvaluationCollector.start();

        EditPlanAiResponse response;

        CreatePlanAiResponse validated;

        Set<Integer> rebuildDays;

        boolean preserveOtherDays;

        TravelPlanContext travelContext = new TravelPlanContext(context.createTravelRequest(), context.healthContexts());

        List<NutritionEvaluationCollector.FoodNutritionEvaluation> evaluations;

        try {
            PlaceCandidateContext candidates = new PlaceCandidateContext();

            PlanEditScope scope = travelRecommendHandler.classifyEditScope(context);

            rebuildDays = planEditValidationHelper.rebuildDays(scope, context);

            preserveOtherDays = !rebuildDays.isEmpty() && scope.preserveOtherDays();

            response = travelRecommendHandler.editPlanByAi(context, candidates);

            CreatePlanAiResponse proposed = new CreatePlanAiResponse(response.planDays());

            validated = preserveOtherDays
                    ? validateRebuildTargets(context, proposed, candidates, rebuildDays)
                    : validatePlaces(proposed, candidates, travelContext, context.currentPlan());

            validated = ensureDaysRebuilt(context, validated, rebuildDays);
        } finally {
            evaluations = nutritionEvaluationCollector.finish();
        }

        CreatePlanAiResponse toFinish = !preserveOtherDays ? validated : new CreatePlanAiResponse(
                validated.planDays().stream().filter(day -> rebuildDays.contains(day.dayNumber())).toList());

        CreatePlanAiResponse finished = finishPlan(toFinish, travelContext, evaluations);

        CreatePlanAiResponse result = !preserveOtherDays ? finished : new CreatePlanAiResponse(
                validated.planDays().stream().map(day -> finished.planDays().stream()
                        .filter(updated -> Objects.equals(updated.dayNumber(), day.dayNumber()))
                        .findFirst().orElse(day)).toList());

        List<String> changes = rebuildDays.isEmpty() ? response.changes() : Stream.concat(
                preserveOtherDays ? Stream.<String>empty() : response.changes().stream(),
                rebuildDays.stream().sorted().map(day -> day + "일차 장소 구성 재구성"))
                .toList();

        return new EditPlanAiResponse(
                response.planName(),
                result.planDays(),
                changes,
                !rebuildDays.isEmpty() || response.processable()
        );
    }

    // 변경 이행 실패 날짜만 최대 두 번 재구성
    private CreatePlanAiResponse ensureDaysRebuilt(
            PlanEditContext context,
            CreatePlanAiResponse response,
            Set<Integer> rebuildDays
    ) {

        CreatePlanAiResponse current = response;

        for (Integer dayNumber : rebuildDays.stream().sorted().toList()) {
            CreatePlanAiResponse.PlanDayDetail target = current.planDays().stream()
                    .filter(day -> Objects.equals(day.dayNumber(), dayNumber)).findFirst()
                    .orElseThrow(() -> planEditValidationHelper.failure("대상 날짜 누락"));

            String reason = "전체 재구성 요청에도 새로운 장소가 없는 " + dayNumber + "일차";

            for (int attempt = 0; !planEditValidationHelper.rebuilt(context, target) && attempt < 2; attempt++) {
                PlaceCandidateContext candidates = new PlaceCandidateContext();

                RebuildPlanDayResponse rebuildResponse = travelRecommendHandler
                        .rebuildDay(
                                context,
                                current,
                                dayNumber,
                                reason,
                                candidates);

                Optional<String> responseFailure = planEditValidationHelper
                        .rebuildFailure(
                                context,
                                dayNumber,
                                rebuildResponse);

                if (responseFailure.isPresent()) {
                    reason = responseFailure.get();

                    log.warn(
                            "[AI DAY REBUILD] attempt={}, targetDay={}, reason={}",
                            attempt + 1,
                            dayNumber,
                            reason);

                    continue;
                }

                CreatePlanAiResponse replacement = new CreatePlanAiResponse(rebuildResponse.planDays());

                Set<String> places = new HashSet<>();

                Set<String> menus = new HashSet<>();

                current.planDays().stream().filter(day -> !Objects.equals(day.dayNumber(), dayNumber))
                        .flatMap(day -> day.schedules().stream())
                        .forEach(slot -> planPlaceHelper.track(slot, places, menus));

                CreatePlanAiResponse checked;

                try {
                    checked = validatePlaces(replacement, candidates,
                            new TravelPlanContext(context.createTravelRequest(), context.healthContexts()),
                            context.currentPlan(), places, menus);
                } catch (BaseException exception) {
                    if (!PlanEditExceptionEnum.INVALID_AI_PLACE.getCode().equals(exception.getErrorCode())) {
                        throw exception;
                    }

                    reason = exception.getMessage();

                    log.warn(
                            "[AI DAY REBUILD] attempt={}, targetDay={}, reason={}",
                            attempt + 1,
                            dayNumber,
                            reason);

                    continue;
                }

                target = checked.planDays().getFirst();

                reason = "원본 검증 후에도 새로운 장소가 없는 " + dayNumber + "일차";

                if (!planEditValidationHelper.rebuilt(context, target)) {
                    log.warn(
                            "[AI DAY REBUILD] attempt={}, targetDay={}, reason={}",
                            attempt + 1,
                            dayNumber,
                            reason);
                }

                CreatePlanAiResponse.PlanDayDetail rebuilt = target;

                current = new CreatePlanAiResponse(current.planDays().stream()
                        .map(day -> Objects.equals(day.dayNumber(), dayNumber) ? rebuilt : day).toList());
            }

            if (!planEditValidationHelper.rebuilt(context, target)) {
                throw planEditValidationHelper.failure(reason);
            }
        }

        return current;
    }

    // 유지 날짜를 먼저 원본 검증·예약한 뒤 재구성 대상만 장소 검증
    private CreatePlanAiResponse validateRebuildTargets(
            PlanEditContext context,
            CreatePlanAiResponse response,
            PlaceCandidateContext candidates,
            Set<Integer> rebuildDays
    ) {

        Set<String> places = new HashSet<>();

        Set<String> menus = new HashSet<>();

        List<CreatePlanAiResponse.PlanDayDetail> preserved = new ArrayList<>();

        for (GetAiPlanResponse.PlanDayDetail day : context.currentPlan().planDays()) {
            if (rebuildDays.contains(day.dayNumber())) {
                continue;
            }

            List<CreatePlanAiResponse.PlanScheduleDetail> schedules = new ArrayList<>();

            for (GetAiPlanResponse.PlanScheduleDetail slot : day.schedules()) {
                Validation validation = planPlaceHelper.verifyExisting(slot, places, menus);

                if (!validation.valid()) {
                    throw invalidPlace(validation.reason());
                }

                schedules.add(validation.schedule());

                planPlaceHelper.track(validation.schedule(), places, menus);
            }

            preserved.add(new CreatePlanAiResponse.PlanDayDetail(day.dayNumber(), day.date(), schedules));
        }

        List<CreatePlanAiResponse.PlanDayDetail> targets = rebuildDays.stream().sorted().map(number -> {
            List<CreatePlanAiResponse.PlanDayDetail> matches = response.planDays().stream()
                    .filter(day -> day != null && Objects.equals(day.dayNumber(), number)).toList();

            CreatePlanAiResponse single = new CreatePlanAiResponse(matches);

            if (!planEditValidationHelper.sameDay(context, number, single)) {
                throw planEditValidationHelper.failure("재구성 대상 일차/날짜 불일치");
            }

            return matches.getFirst();
        }).toList();

        CreatePlanAiResponse checked = validatePlaces(new CreatePlanAiResponse(targets), candidates,
                new TravelPlanContext(context.createTravelRequest(), context.healthContexts()),
                context.currentPlan(), places, menus);

        return new CreatePlanAiResponse(Stream.concat(preserved.stream(), checked.planDays().stream())
                .sorted(Comparator.comparing(CreatePlanAiResponse.PlanDayDetail::dayNumber)).toList());
    }

    // 확정 장소와 시간에 따른 복약·태그·누락 이동시간 보정
    private CreatePlanAiResponse finishPlan(
            CreatePlanAiResponse response,
            TravelPlanContext context,
            List<NutritionEvaluationCollector.FoodNutritionEvaluation> evaluations
    ) {

        CreatePlanAiResponse medicationFixed = ensureMedicationSchedules(
                response,
                context.healthContexts()
        );

        CreatePlanAiResponse travelFixed = fillMissingTravelMinutes(
                medicationFixed,
                context.createTravelRequest()
        );

        validateTravelMinutes(travelFixed);

        CreatePlanAiResponse normalized = normalizeScheduleTimes(
                travelFixed,
                context.healthContexts()
        );

        CreatePlanAiResponse finalMedicationFixed = ensureMedicationSchedules(
                normalized,
                context.healthContexts()
        );

        CreatePlanAiResponse tagged = applyDeterministicTags(
                finalMedicationFixed,
                context,
                evaluations
        );

        validateMealTimes(
                tagged,
                context.healthContexts()
        );

        return tagged;
    }

    // Plan 객체 단건 조회하기 (존재 검증은 호출부에서 이미 끝난 상태를 전제)
    public Plan findPlanById(Long planId){

        return planRepository.getReferenceById(planId);
    }

    // planDays에 붙은 모든 RecommendationTag를 모아 Plan 전체 태그로 집계하기
    // CreatePlanAiResponse.PlanDayDetail을 EditPlanAiResponse도 재사용하므로 Create/Edit 공통 사용
    public Set<RecommendationTag> aggregateTags(
            List<CreatePlanAiResponse.PlanDayDetail> planDays
    ) {

        return planDays.stream()
                .flatMap(planDay -> planDay.schedules().stream())
                .flatMap(schedule -> nullSafeTags(schedule).stream())
                .collect(Collectors.toSet());
    }

    // 검색 원본 검증 및 실패 슬롯별 제한 복구
    private CreatePlanAiResponse validatePlaces(
            CreatePlanAiResponse response,
            PlaceCandidateContext candidates,
            TravelPlanContext context,
            GetAiPlanResponse existing
    ) {

        return validatePlaces(response, candidates, context, existing, new HashSet<>(), new HashSet<>());
    }

    // 다른 날짜의 예약 장소·메뉴를 포함한 장소 검증
    private CreatePlanAiResponse validatePlaces(
            CreatePlanAiResponse response,
            PlaceCandidateContext candidates,
            TravelPlanContext context,
            GetAiPlanResponse existing,
            Set<String> usedPlaces,
            Set<String> usedMenus
    ) {

        if (response == null || response.planDays() == null || response.planDays().isEmpty()) {
            throw invalidPlace("일정 누락");
        }

        validatePlanDays(
                response,
                context.createTravelRequest()
        );

        validateTouristPlaceCounts(
                response,
                context.healthContexts()
        );

        response = normalizeScheduleTimes(
                response,
                context.healthContexts()
        );

        response = ensureMedicationSchedules(
                response,
                context.healthContexts()
        );

        validateMealTimes(
                response,
                context.healthContexts()
        );

        List<Validation> validations = new ArrayList<>();

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            if (day == null || day.schedules() == null || day.schedules().isEmpty()) {
                throw invalidPlace("일정 슬롯 누락");
            }

            for (CreatePlanAiResponse.PlanScheduleDetail slot : day.schedules()) {
                Validation validation = planPlaceHelper.validate(slot, candidates, usedPlaces, usedMenus);

                validations.add(validation);

                if (validation.valid()) {
                    planPlaceHelper.track(validation.schedule(), usedPlaces, usedMenus);
                }
            }
        }
        // 뒤쪽 정상 슬롯까지 예약하여 앞쪽 실패 슬롯의 재선택 대상에서 제외
        int validationIndex = 0;

        List<CreatePlanAiResponse.PlanDayDetail> days = new ArrayList<>();
        String previousLocation = context.createTravelRequest().decidedLocation();
        CreatePlanAiResponse.PlanScheduleDetail previousPlace = null;

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules = new ArrayList<>();

            boolean changedRoute = false;

            LocalTime previousEnd = null;

            for (CreatePlanAiResponse.PlanScheduleDetail slot : day.schedules()) {
                Validation validation = validations.get(validationIndex++);

                CreatePlanAiResponse.PlanScheduleDetail resolved = recoverPlace(
                        slot, validation, day, existing, context, usedPlaces, usedMenus);

                boolean changed = !Objects.equals(slot.locationName(), resolved.locationName())
                        || !Objects.equals(slot.longitude(), resolved.longitude())
                        || !Objects.equals(slot.latitude(), resolved.latitude())
                        || changedFromExisting(existing, day, resolved);

                changedRoute = changedRoute || changed;

                if (changedRoute && planPlaceHelper.requiresPlace(resolved)) {
                    resolved = recalculateSlot(resolved, previousLocation, previousPlace, previousEnd, context.createTravelRequest());
                }

                schedules.add(resolved);

                planPlaceHelper.track(resolved, usedPlaces, usedMenus);

                if (planPlaceHelper.requiresPlace(resolved)) {
                    previousLocation = resolved.locationName();

                    previousPlace = resolved;

                    previousEnd = resolved.endTime();
                }
            }

            days.add(new CreatePlanAiResponse.PlanDayDetail(day.dayNumber(), day.date(), schedules));
        }

        return new CreatePlanAiResponse(days);
    }

    private void validatePlanDays(
            CreatePlanAiResponse response,
            CreateTravelRequest request
    ) {

        Set<Integer> dayNumbers = new HashSet<>();
        int lastDayNumber = request.dateType().getPlusDays() + 1;

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            if (day == null || day.dayNumber() == null || day.date() == null) {
                throw invalidPlace("일차 또는 날짜 누락");
            }

            if (day.dayNumber() < 1 || day.dayNumber() > lastDayNumber
                    || !dayNumbers.add(day.dayNumber())) {
                throw invalidPlace("유효하지 않거나 중복된 일차");
            }

            if (!day.date().equals(request.startDate().plusDays(day.dayNumber() - 1L))) {
                throw invalidPlace("일차와 날짜 불일치");
            }
        }
    }

    private void validateTouristPlaceCounts(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        if (healthContexts == null || healthContexts.isEmpty()) {
            return;
        }

        boolean hasMinimalTraveler = healthContexts
                .stream()
                .anyMatch(context -> context.walkType() == WalkType.MINIMAL);

        int expectedCount = hasMinimalTraveler ? 2 : 3;

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            long touristPlaceCount = day.schedules() == null
                    ? 0
                    : day
                            .schedules()
                            .stream()
                            .filter(Objects::nonNull)
                            .map(CreatePlanAiResponse.PlanScheduleDetail::courseType)
                            .filter(type -> type == CourseType.ATTRACTION
                                    || type == CourseType.MUST_HAVE)
                            .count();

            if (touristPlaceCount != expectedCount) {
                throw invalidPlace(
                        "관광 장소 개수 불일치: day=" + day.dayNumber()
                                + ", expected=" + expectedCount
                                + ", actual=" + touristPlaceCount
                );
            }
        }
    }

    private void validateTravelMinutes(CreatePlanAiResponse response) {

        boolean invalid = response
                .planDays()
                .stream()
                .flatMap(day -> day
                        .schedules()
                        .stream())
                .filter(planPlaceHelper::requiresPlace)
                .anyMatch(schedule -> schedule.travelMinutes() == null
                        || schedule.travelMinutes() < 0);

        if (invalid) {
            throw invalidPlace("이동시간 누락 또는 유효하지 않음");
        }
    }

    private CreatePlanAiResponse normalizeScheduleTimes(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        if (response == null || response.planDays() == null) {
            return response;
        }

        return new CreatePlanAiResponse(
                response
                        .planDays()
                        .stream()
                        .map(day -> normalizeScheduleTimes(day, healthContexts))
                        .toList());
    }

    private CreatePlanAiResponse.PlanDayDetail normalizeScheduleTimes(
            CreatePlanAiResponse.PlanDayDetail day,
            List<TravelHealthContext> healthContexts
    ) {

        if (day == null || day.schedules() == null) {
            return day;
        }

        List<CreatePlanAiResponse.PlanScheduleDetail> schedules = new ArrayList<>();

        LocalTime previousPlaceEnd = null;
        LocalTime previousMealEnd = null;
        int movableStartIndex = 0;

        for (CreatePlanAiResponse.PlanScheduleDetail schedule : day.schedules()) {
            if (schedule == null || schedule.startTime() == null || schedule.stayMinutes() == null
                    || schedule.stayMinutes() <= 0 || !planPlaceHelper.requiresPlace(schedule)) {
                schedules.add(schedule);

                continue;
            }

            LocalTime startTime = schedule.startTime();

            MealTimeRange mealTimeRange = mealTimeRange(
                    schedule.scheduleType(),
                    healthContexts
            );

            if (previousPlaceEnd != null && schedule.travelMinutes() != null
                    && schedule.travelMinutes() >= 0) {

                LocalTime earliestStart = previousPlaceEnd
                        .plusMinutes(schedule.travelMinutes());

                if (mealTimeRange != null && earliestStart.isAfter(mealTimeRange.latest())) {
                    long shiftMinutes = Duration
                            .between(
                                    mealTimeRange.latest(),
                                    earliestStart
                            )
                            .toMinutes();

                    shiftPreviousPlaces(
                            schedules,
                            movableStartIndex,
                            shiftMinutes,
                            previousMealEnd
                    );

                    previousPlaceEnd = previousPlaceEnd.minusMinutes(shiftMinutes);
                    earliestStart = previousPlaceEnd.plusMinutes(schedule.travelMinutes());
                }

                if (earliestStart.isAfter(startTime)) {
                    startTime = earliestStart;
                }
            }

            if (mealTimeRange != null) {
                if (startTime.isBefore(mealTimeRange.earliest())) {
                    startTime = mealTimeRange.earliest();
                }

                if (startTime.isAfter(mealTimeRange.latest())) {
                    startTime = mealTimeRange.latest();
                }
            }

            LocalTime endTime = startTime
                    .plusMinutes(schedule.stayMinutes());

            schedules.add(withScheduleTimes(
                    schedule,
                    startTime,
                    endTime));

            previousPlaceEnd = endTime;

            if (mealTimeRange != null) {
                previousMealEnd = endTime;
                movableStartIndex = schedules.size();
            }
        }

        return new CreatePlanAiResponse.PlanDayDetail(
                day.dayNumber(),
                day.date(),
                schedules);
    }

    private MealTimeRange mealTimeRange(
            ScheduleType scheduleType,
            List<TravelHealthContext> healthContexts
    ) {

        if (!MEAL_SCHEDULE_TYPES.contains(scheduleType)) {
            return null;
        }

        List<LocalTime> configuredTimes = healthContexts
                .stream()
                .map(TravelHealthContext::mealInfo)
                .map(mealInfo -> configuredMealTime(mealInfo, scheduleType))
                .filter(Objects::nonNull)
                .toList();

        if (configuredTimes.isEmpty()) {
            return null;
        }

        LocalTime earliest = configuredTimes
                .stream()
                .map(time -> time.minusMinutes(MEAL_TIME_TOLERANCE_MINUTES))
                .max(LocalTime::compareTo)
                .orElseThrow();

        LocalTime latest = configuredTimes
                .stream()
                .map(time -> time.plusMinutes(MEAL_TIME_TOLERANCE_MINUTES))
                .min(LocalTime::compareTo)
                .orElseThrow();

        if (earliest.isAfter(latest)) {
            throw invalidPlace("여행자 식사시간 허용 범위 불일치");
        }

        return new MealTimeRange(
                earliest,
                latest
        );
    }

    private void shiftPreviousPlaces(
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules,
            int fromIndex,
            long shiftMinutes,
            LocalTime previousMealEnd
    ) {

        int firstPlaceIndex = -1;

        for (int index = fromIndex; index < schedules.size(); index++) {
            CreatePlanAiResponse.PlanScheduleDetail schedule = schedules.get(index);

            if (schedule != null && planPlaceHelper.requiresPlace(schedule)) {
                firstPlaceIndex = index;
                break;
            }
        }

        if (firstPlaceIndex < 0) {
            throw invalidPlace(
                    "식사시간을 만족할 수 없는 일정: 앞당길 장소가 없음"
                            + " / fromIndex=" + fromIndex
                            + " / shiftMinutes=" + shiftMinutes
                            + " / scheduleSize=" + schedules.size()
            );
        }

        CreatePlanAiResponse.PlanScheduleDetail firstPlace = schedules.get(firstPlaceIndex);

        LocalTime shiftedFirstStart = firstPlace.startTime().minusMinutes(shiftMinutes);

        if (shiftedFirstStart.isAfter(firstPlace.startTime())) {
            throw invalidPlace(
                    "식사시간을 만족할 수 없는 일정: 앞당긴 시작시간이 원래 시작시간보다 늦음"
                            + " / locationName=" + firstPlace.locationName()
                            + " / originalStart=" + firstPlace.startTime()
                            + " / shiftedStart=" + shiftedFirstStart
                            + " / shiftMinutes=" + shiftMinutes
            );
        }

        if (previousMealEnd != null) {
            LocalTime earliestStart = previousMealEnd.plusMinutes(
                    firstPlace.travelMinutes() == null
                            ? 0
                            : firstPlace.travelMinutes()
            );

            if (shiftedFirstStart.isBefore(earliestStart)) {
                throw invalidPlace(
                        "식사시간을 만족할 수 없는 일정: 이전 식사 종료 시각 이전으로 앞당겨짐"
                                + " / locationName=" + firstPlace.locationName()
                                + " / shiftedStart=" + shiftedFirstStart
                                + " / earliestStart=" + earliestStart
                                + " / previousMealEnd=" + previousMealEnd
                                + " / travelMinutes=" + firstPlace.travelMinutes()
                                + " / shiftMinutes=" + shiftMinutes
                );
            }
        }

        for (int index = firstPlaceIndex; index < schedules.size(); index++) {
            CreatePlanAiResponse.PlanScheduleDetail schedule = schedules.get(index);

            if (schedule == null || !planPlaceHelper.requiresPlace(schedule)) {
                continue;
            }

            schedules.set(
                    index,
                    withScheduleTimes(
                            schedule,
                            schedule.startTime().minusMinutes(shiftMinutes),
                            schedule.endTime().minusMinutes(shiftMinutes)
                    )
            );
        }
    }

    private record MealTimeRange(
            LocalTime earliest,
            LocalTime latest
    ) {
    }

    private CreatePlanAiResponse.PlanScheduleDetail withScheduleTimes(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            LocalTime startTime,
            LocalTime endTime
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                schedule.scheduleType(),
                schedule.courseType(),
                startTime,
                endTime,
                schedule.locationName(),
                schedule.location(),
                schedule.longitude(),
                schedule.latitude(),
                schedule.imageUrl(),
                schedule.thumbNailImageUrl(),
                schedule.stayMinutes(),
                schedule.travelMinutes(),
                schedule.tags(),
                schedule.medication(),
                schedule.restaurantDetail(),
                schedule.candidateId());
    }

    private void validateMealTimes(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        if (response == null || response.planDays() == null) {
            return;
        }

        for (CreatePlanAiResponse.PlanDayDetail planDay : response.planDays()) {
            if (planDay == null || planDay.schedules() == null) {
                continue;
            }

            for (CreatePlanAiResponse.PlanScheduleDetail schedule : planDay.schedules()) {
                if (schedule == null || schedule.startTime() == null
                        || !MEAL_SCHEDULE_TYPES.contains(schedule.scheduleType())) {
                    continue;
                }

                for (TravelHealthContext healthContext : healthContexts) {
                    LocalTime configuredMealTime = configuredMealTime(
                            healthContext.mealInfo(),
                            schedule.scheduleType()
                    );

                    if (configuredMealTime == null) {
                        continue;
                    }

                    long difference = Math.abs(
                            Duration.between(
                                    configuredMealTime,
                                    schedule.startTime()
                            ).toMinutes()
                    );

                    if (difference > MEAL_TIME_TOLERANCE_MINUTES) {
                        throw invalidPlace("식사시간 허용 범위 초과");
                    }
                }
            }
        }
    }

    private LocalTime configuredMealTime(
            TravelHealthContext.MealInfoContext mealInfo,
            ScheduleType scheduleType
    ) {

        if (mealInfo == null || !mealInfo.applied()) {
            return null;
        }

        return switch (scheduleType) {
            case BREAKFAST -> mealInfo.breakfastApplied()
                    ? mealInfo.breakfastTime()
                    : null;

            case LUNCH -> mealInfo.lunchApplied()
                    ? mealInfo.lunchTime()
                    : null;

            case DINNER -> mealInfo.dinnerApplied()
                    ? mealInfo.dinnerTime()
                    : null;

            default -> null;
        };
    }

    // 편집 전 동일 슬롯과 장소 비교를 통한 기존 이동시간 무효화
    private boolean changedFromExisting(
            GetAiPlanResponse existing,
            CreatePlanAiResponse.PlanDayDetail day,
            CreatePlanAiResponse.PlanScheduleDetail slot
    ) {

        if (existing == null || !planPlaceHelper.requiresPlace(slot)) {
            return false;
        }

        return existing.planDays().stream()
                .filter(oldDay -> Objects.equals(oldDay.date(), day.date())
                        && Objects.equals(oldDay.dayNumber(), day.dayNumber()))
                .flatMap(oldDay -> oldDay.schedules().stream())
                .noneMatch(old -> old.scheduleType() == slot.scheduleType() && old.courseType() == slot.courseType()
                        && Objects.equals(old.startTime(), slot.startTime())
                        && Objects.equals(old.locationName(), slot.locationName())
                        && Objects.equals(old.longitude(), slot.longitude())
                        && Objects.equals(old.latitude(), slot.latitude()));
    }

    // 정상 슬롯 유지 및 실패 이유를 포함한 최대 두 번의 장소 재선택
    private CreatePlanAiResponse.PlanScheduleDetail recoverPlace(
            CreatePlanAiResponse.PlanScheduleDetail slot,
            Validation validation,
            CreatePlanAiResponse.PlanDayDetail day,
            GetAiPlanResponse existing,
            TravelPlanContext context,
            Set<String> usedPlaces,
            Set<String> usedMenus
    ) {

        if (validation.valid()) {
            return validation.schedule();
        }

        if (slot == null) {
            throw invalidPlace(validation.reason());
        }

        Validation result = validation;

        for (int attempt = 0; attempt < 2; attempt++) {
            PlaceCandidateContext retryCandidates = new PlaceCandidateContext();

            CreatePlanAiResponse.PlanScheduleDetail choice = travelRecommendHandler.reselectPlace(
                    new PlaceReselectPrompt(context, slot, result.reason(), Set.copyOf(usedPlaces), Set.copyOf(usedMenus)),
                    retryCandidates);

            result = planPlaceHelper.validate(planPlaceHelper.select(slot, choice), retryCandidates, usedPlaces, usedMenus);

            if (result.valid()) {
                return result.schedule();
            }
        }

        String finalReason = result.reason();

        return verifiedFallback(existing, day, slot, usedPlaces, usedMenus)
                .orElseThrow(() -> invalidPlace("day=" + day.dayNumber() + ", time=" + slot.startTime()
                        + ", " + finalReason));
    }

    // 동일 날짜·시간·유형의 기존 슬롯만 출처 재검증 후 원본 복원
    private Optional<CreatePlanAiResponse.PlanScheduleDetail> verifiedFallback(
            GetAiPlanResponse existing,
            CreatePlanAiResponse.PlanDayDetail day,
            CreatePlanAiResponse.PlanScheduleDetail slot,
            Set<String> usedPlaces,
            Set<String> usedMenus
    ) {

        if (existing == null || existing.planDays() == null) {
            return Optional.empty();
        }

        return existing.planDays().stream()
                .filter(oldDay -> Objects.equals(oldDay.date(), day.date())
                        && Objects.equals(oldDay.dayNumber(), day.dayNumber()))
                .flatMap(oldDay -> oldDay.schedules().stream())
                .filter(old -> old.scheduleType() == slot.scheduleType() && old.courseType() == slot.courseType()
                        && Objects.equals(old.startTime(), slot.startTime()))
                .map(old -> planPlaceHelper.verifyExisting(old, usedPlaces, usedMenus))
                .filter(Validation::valid)
                .map(Validation::schedule)
                .findFirst();
    }

    // 장소 변경 이후 구간의 이동시간 및 도착 이후 일정 시간 재계산
    private CreatePlanAiResponse.PlanScheduleDetail recalculateSlot(
            CreatePlanAiResponse.PlanScheduleDetail slot,
            String previousLocation,
            CreatePlanAiResponse.PlanScheduleDetail previousPlace,
            LocalTime previousEnd,
            CreateTravelRequest request
    ) {

        KakaoRouteResult route = lookupRoute(previousLocation, previousPlace, slot, request.transportation());

        if (route == null || route.travelMinutes() == null || route.travelMinutes() < 0) {
            throw invalidPlace("장소 변경 후 이동시간 확인 실패: origin=" + previousLocation
                    + ", destination=" + slot.locationName() + ", transportation=" + request.transportation());
        }

        LocalTime arrival = previousEnd == null ? slot.startTime() : previousEnd.plusMinutes(route.travelMinutes());

        LocalTime start = arrival.isAfter(slot.startTime()) ? arrival : slot.startTime();

        LocalTime end = start.plusMinutes(slot.stayMinutes());

        if (!end.isAfter(start) || previousEnd != null && arrival.isBefore(previousEnd)) {
            throw invalidPlace("장소 변경 후 일정의 날짜 초과");
        }

        return new CreatePlanAiResponse.PlanScheduleDetail(
                slot.scheduleType(),
                slot.courseType(),
                start,
                end,
                slot.locationName(),
                slot.location(),
                slot.longitude(),
                slot.latitude(),
                slot.imageUrl(),
                slot.thumbNailImageUrl(),
                slot.stayMinutes(),
                route.travelMinutes(),
                slot.tags(),
                slot.medication(),
                slot.restaurantDetail(),
                slot.candidateId()
        );
    }

    // 기존 API 오류 형식의 장소 검증 실패
    private BaseException invalidPlace(String reason) {

        return new BaseException(PlanEditExceptionEnum.INVALID_AI_PLACE, new Object[]{reason});
    }

    // AI 복약 일정을 제거하고 healthContexts 기준으로 Java가 다시 생성
    private CreatePlanAiResponse ensureMedicationSchedules(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        List<CreatePlanAiResponse.PlanDayDetail> fixedPlanDays =
                response.planDays()
                        .stream()
                        .map(planDay -> fixDayMedicationSchedules(planDay, healthContexts))
                        .toList();

        return new CreatePlanAiResponse(fixedPlanDays);
    }

    // 하루치 AI MEDICATION 슬롯을 제거하고 실제 식사시간 또는 등록 식사시간 기준으로 교체
    private CreatePlanAiResponse.PlanDayDetail fixDayMedicationSchedules(
            CreatePlanAiResponse.PlanDayDetail planDay,
            List<TravelHealthContext> healthContexts
    ) {

        List<CreatePlanAiResponse.PlanScheduleDetail> nonMedicationSchedules =
                planDay.schedules()
                        .stream()
                        .filter(schedule -> schedule.courseType() != CourseType.MEDICATION)
                        .toList();

        Map<ScheduleType, LocalTime> dayMealTimes =
                dayMealTimes(nonMedicationSchedules);

        List<CreatePlanAiResponse.PlanScheduleDetail> medicationSchedules =
                healthContexts.stream()
                        .flatMap(healthContext -> medicationSchedulesFor(healthContext, dayMealTimes).stream())
                        .collect(Collectors.groupingBy(CreatePlanAiResponse.PlanScheduleDetail::startTime))
                        .values()
                        .stream()
                        .map(this::mergeSameTimeMedicationSchedules)
                        .toList();

        List<CreatePlanAiResponse.PlanScheduleDetail> mergedSchedules =
                new ArrayList<>(nonMedicationSchedules);

        mergedSchedules.addAll(medicationSchedules);

        mergedSchedules.sort(Comparator.comparing(CreatePlanAiResponse.PlanScheduleDetail::startTime));

        return new CreatePlanAiResponse.PlanDayDetail(
                planDay.dayNumber(),
                planDay.date(),
                mergedSchedules
        );
    }

    // 그 날짜 실제 식사 일정(BREAKFAST/LUNCH/DINNER)의 시작시간
    private Map<ScheduleType, LocalTime> dayMealTimes(
            List<CreatePlanAiResponse.PlanScheduleDetail> nonMedicationSchedules
    ) {

        return nonMedicationSchedules.stream()
                .filter(schedule -> MEAL_SCHEDULE_TYPES.contains(schedule.scheduleType()))
                .filter(schedule -> schedule.startTime() != null)
                .collect(
                        Collectors.toMap(
                                CreatePlanAiResponse.PlanScheduleDetail::scheduleType,
                                CreatePlanAiResponse.PlanScheduleDetail::startTime,
                                (first, second) -> first
                        )
                );
    }

    // 한 여행자의 모든 복약 정보를 그 날짜의 복약 슬롯 목록으로 변환
    private List<CreatePlanAiResponse.PlanScheduleDetail> medicationSchedulesFor(
            TravelHealthContext healthContext,
            Map<ScheduleType, LocalTime> dayMealTimes
    ) {

        return healthContext.medicationInfos()
                .stream()
                .flatMap(medicationInfo ->
                        medicationSchedulesFor(healthContext, medicationInfo, dayMealTimes).stream()
                )
                .toList();
    }

    // 복약 기준(medicationBasis)에 따라 한 복약 정보에서 그 날짜의 복약 슬롯(들)을 계산
    private List<CreatePlanAiResponse.PlanScheduleDetail> medicationSchedulesFor(
            TravelHealthContext healthContext,
            TravelHealthContext.MedicationInfoContext medicationInfo,
            Map<ScheduleType, LocalTime> dayMealTimes
    ) {

        boolean usesMealRules =
                (medicationInfo.medicationBasis() == MedicationBasis.WITH_MEAL
                        || medicationInfo.medicationBasis() == MedicationBasis.UNKNOWN)
                        && !medicationInfo.mealMedicationRules().isEmpty();

        if (!usesMealRules) {
            return List.of(
                    medicationSchedule(
                            medicationInfo.medicationTime(),
                            null,
                            medicationInfo.drugName() + " 복용"
                    )
            );
        }

        return medicationInfo.mealMedicationRules()
                .stream()
                .map(rule -> medicationScheduleForRule(healthContext, medicationInfo, rule, dayMealTimes))
                .toList();
    }

    // 식사 연동 복약 규칙 하나를 실제 시각의 복약 슬롯으로 변환
    private CreatePlanAiResponse.PlanScheduleDetail medicationScheduleForRule(
            TravelHealthContext healthContext,
            TravelHealthContext.MedicationInfoContext medicationInfo,
            TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext rule,
            Map<ScheduleType, LocalTime> dayMealTimes
    ) {

        if (rule.mealTiming() == MealTiming.REGARDLESS_OF_MEAL) {
            return medicationSchedule(
                    medicationInfo.medicationTime(),
                    null,
                    medicationInfo.drugName() + " 복용"
            );
        }

        LocalTime mealTime =
                mealTimeFor(healthContext, rule.relatedMeal(), dayMealTimes);

        if (mealTime == null) {
            throw invalidPlace("복약 기준 식사시간 누락");
        }

        LocalTime medicationTime =
                applyMealTiming(mealTime, rule.mealTiming(), rule.intervalMinutes());

        String description =
                medicationInfo.drugName()
                        + " " + rule.relatedMeal().getCodeName()
                        + " " + rule.mealTiming().getCodeName()
                        + (rule.intervalMinutes() != null && rule.intervalMinutes() > 0
                                ? " " + rule.intervalMinutes() + "분"
                                : "");

        return medicationSchedule(
                medicationTime,
                rule.intervalMinutes(),
                description
        );
    }

    // relatedMeal에 해당하는 그 날짜의 실제 식사시간, 그 날 식사 일정이 없으면
    // 여행자가 등록한 기준 식사시간(mealInfo)으로 대체
    private LocalTime mealTimeFor(
            TravelHealthContext healthContext,
            RelatedMeal relatedMeal,
            Map<ScheduleType, LocalTime> dayMealTimes
    ) {

        ScheduleType scheduleType =
                switch (relatedMeal) {
                    case BREAKFAST -> ScheduleType.BREAKFAST;

                    case LUNCH -> ScheduleType.LUNCH;

                    case DINNER -> ScheduleType.DINNER;
                };

        TravelHealthContext.MealInfoContext mealInfo =
                healthContext.mealInfo();

        LocalTime configuredMealTime =
                mealInfo == null
                        ? null
                        : switch (relatedMeal) {
                            case BREAKFAST -> mealInfo.breakfastTime();

                            case LUNCH -> mealInfo.lunchTime();

                            case DINNER -> mealInfo.dinnerTime();
                        };

        return dayMealTimes.getOrDefault(scheduleType, configuredMealTime);
    }

    // mealTiming/intervalMinutes를 기준시간에 적용한 실제 복약시각
    private LocalTime applyMealTiming(
            LocalTime mealTime,
            MealTiming mealTiming,
            Integer intervalMinutes
    ) {

        int minutes = intervalMinutes == null ? 0 : intervalMinutes;

        return switch (mealTiming) {
            case BEFORE_MEAL -> mealTime.minusMinutes(minutes);

            case AFTER_MEAL -> mealTime.plusMinutes(minutes);

            case DURING_MEAL, REGARDLESS_OF_MEAL -> mealTime;
        };
    }

    // MEDICATION CourseType 슬롯 하나 생성 (체류시간 10분 고정)
    private CreatePlanAiResponse.PlanScheduleDetail medicationSchedule(
            LocalTime startTime,
            Integer intervalMinutes,
            String description
    ) {

        if (startTime == null) {
            throw invalidPlace("복약 기준시간 누락");
        }

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.CHECK_IN,
                CourseType.MEDICATION,
                startTime,
                startTime.plusMinutes(10),
                null,
                null,
                null,
                null,
                null,
                null,
                10,
                null,
                Set.of(),
                new CreatePlanAiResponse.MedicationSchedule(
                        intervalMinutes,
                        description
                ),
                null
        );
    }

    // 같은 시각에 겹치는 여러 복약 슬롯(다른 여행자·다른 약)을 하나로 병합
    private CreatePlanAiResponse.PlanScheduleDetail mergeSameTimeMedicationSchedules(
            List<CreatePlanAiResponse.PlanScheduleDetail> sameTimeSchedules
    ) {

        if (sameTimeSchedules.size() == 1) {
            return sameTimeSchedules.get(0);
        }

        String mergedDescription =
                sameTimeSchedules.stream()
                        .map(schedule -> schedule.medication().description())
                        .collect(Collectors.joining(", "));

        Integer representativeInterval =
                sameTimeSchedules.stream()
                        .map(schedule -> schedule.medication().intervalMinutes())
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

        return medicationSchedule(
                sameTimeSchedules.get(0).startTime(),
                representativeInterval,
                mergedDescription
        );
    }

    // STEP 6/STEP 9에서 AI 판단 없이 결정 가능한 RecommendationTag를 Java에서 보강
    // 기존 AI 판단 태그는 유지하고, 결정 가능한 태그만 합집합으로 추가
    private CreatePlanAiResponse applyDeterministicTags(
            CreatePlanAiResponse response,
            TravelPlanContext travelPlanContext,
            List<NutritionEvaluationCollector.FoodNutritionEvaluation> nutritionEvaluations
    ) {

        CreateTravelRequest createTravelRequest = travelPlanContext.createTravelRequest();

        Map<String, List<NutritionEvaluationResult>> resultsByFoodName =
                nutritionEvaluationsByFoodName(nutritionEvaluations);

        boolean hasAllergyOrAvoidFood =
                hasAllergyOrAvoidFood(travelPlanContext.healthContexts());

        List<CreatePlanAiResponse.PlanDayDetail> taggedPlanDays =
                response.planDays()
                        .stream()
                        .map(planDay ->
                                new CreatePlanAiResponse.PlanDayDetail(
                                        planDay.dayNumber(),
                                        planDay.date(),
                                        planDay.schedules()
                                                .stream()
                                                .map(schedule ->
                                                        addDeterministicTags(
                                                                schedule,
                                                                createTravelRequest,
                                                                resultsByFoodName,
                                                                hasAllergyOrAvoidFood
                                                        )
                                                )
                                                .toList()
                                )
                        )
                        .toList();

        return new CreatePlanAiResponse(taggedPlanDays);
    }

    // 한 슬롯에 결정 가능한 태그를 계산해 기존 태그와 합집합으로 병합
    private CreatePlanAiResponse.PlanScheduleDetail addDeterministicTags(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            CreateTravelRequest createTravelRequest,
            Map<String, List<NutritionEvaluationResult>> resultsByFoodName,
            boolean hasAllergyOrAvoidFood
    ) {

        Set<RecommendationTag> deterministicTags =
                deterministicTagsFor(
                        schedule,
                        createTravelRequest,
                        resultsByFoodName,
                        hasAllergyOrAvoidFood
                );

        if (deterministicTags.isEmpty()) {
            return schedule;
        }

        Set<RecommendationTag> mergedTags = new HashSet<>(nullSafeTags(schedule));

        mergedTags.addAll(deterministicTags);

        return new CreatePlanAiResponse.PlanScheduleDetail(
                schedule.scheduleType(),
                schedule.courseType(),
                schedule.startTime(),
                schedule.endTime(),
                schedule.locationName(),
                schedule.location(),
                schedule.longitude(),
                schedule.latitude(),
                schedule.imageUrl(),
                schedule.thumbNailImageUrl(),
                schedule.stayMinutes(),
                schedule.travelMinutes(),
                mergedTags,
                schedule.medication(),
                schedule.restaurantDetail(),
                schedule.candidateId()
        );
    }

    // CourseType별로 결정 가능한 태그 계산을 분기 (그 외 CourseType은 AI 판단 영역이라 빈 집합)
    private Set<RecommendationTag> deterministicTagsFor(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            CreateTravelRequest createTravelRequest,
            Map<String, List<NutritionEvaluationResult>> resultsByFoodName,
            boolean hasAllergyOrAvoidFood
    ) {

        return switch (schedule.courseType()) {
            case MEDICATION -> Set.of(RecommendationTag.MEDICATION_SCHEDULE);

            case TRANSPORTATION -> transportationTag(createTravelRequest.transportation());

            case RESTAURANT, LOCAL_FOOD -> restaurantTags(
                    schedule.restaurantDetail(),
                    createTravelRequest,
                    resultsByFoodName,
                    hasAllergyOrAvoidFood
            );

            default -> Set.of();
        };
    }

    // 이동수단 태그: WALKING은 Transportation enum에 대응값이 없어 AI 판단 영역으로 남김
    private Set<RecommendationTag> transportationTag(Transportation transportation) {

        return switch (transportation) {
            case CAR -> Set.of(RecommendationTag.CAR);

            case TRANSIT -> Set.of(RecommendationTag.TRANSIT);
        };
    }

    // RESTAURANT/LOCAL_FOOD 슬롯 전용: 지역음식/영양참고/알레르기확인 태그 결정
    // restaurantDetail이 없는 슬롯(Tool 확정 실패)은 판단 근거가 없어 빈 집합
    private Set<RecommendationTag> restaurantTags(
            CreatePlanAiResponse.RestaurantDetail restaurantDetail,
            CreateTravelRequest createTravelRequest,
            Map<String, List<NutritionEvaluationResult>> resultsByFoodName,
            boolean hasAllergyOrAvoidFood
    ) {

        if (restaurantDetail == null || isBlank(restaurantDetail.menuName())) {
            return Set.of();
        }

        Set<RecommendationTag> tags = new HashSet<>();

        if (matchesLocalFood(restaurantDetail.menuName(), createTravelRequest)) {
            tags.add(RecommendationTag.LOCAL_FOOD);
        }

        tags.addAll(
                nutritionReferenceTags(
                        restaurantDetail.menuName(),
                        resultsByFoodName
                )
        );

        if (hasAllergyOrAvoidFood) {
            tags.add(RecommendationTag.ALLERGY_CHECK);
        }

        return tags;
    }

    // 여행 요청의 지역음식 후보(localFoods, recommendFoods)와 실제 메뉴명 대조
    private boolean matchesLocalFood(
            String menuName,
            CreateTravelRequest createTravelRequest
    ) {

        return Stream.concat(
                        createTravelRequest.localFoods().stream(),
                        createTravelRequest.recommendFoods().stream()
                )
                .filter(food -> !isBlank(food))
                .anyMatch(food -> menuName.contains(food) || food.contains(menuName));
    }

    // 실제 메뉴 기준 영양평가 결과(CHECK/HIGH)를 참고 태그로 변환
    // 같은 메뉴를 여러 여행자 질환 기준으로 평가했다면 결과를 모두 반영
    private Set<RecommendationTag> nutritionReferenceTags(
            String menuName,
            Map<String, List<NutritionEvaluationResult>> resultsByFoodName
    ) {

        return resultsByFoodName
                .getOrDefault(menuName, List.of())
                .stream()
                .filter(result -> result.status() == NutritionEvaluationStatus.AVAILABLE)
                .flatMap(result -> result.evaluations().stream())
                .filter(detail -> detail.nutritionLevel() != NutritionLevel.LOW)
                .map(detail -> NUTRITION_REFERENCE_TAGS.get(detail.nutritionType()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // 여행자 중 알레르기/기피 음식이 등록된 사람이 있는지 확인
    private boolean hasAllergyOrAvoidFood(List<TravelHealthContext> healthContexts) {

        return healthContexts.stream()
                .flatMap(healthContext -> healthContext.foodInfos().stream())
                .anyMatch(foodInfo ->
                        foodInfo.foodType() == FoodType.ALLERGY
                                || foodInfo.foodType() == FoodType.AVOID
                );
    }

    // evaluateFoodNutrition Tool 호출 기록을 메뉴명 기준으로 재구성
    private Map<String, List<NutritionEvaluationResult>> nutritionEvaluationsByFoodName(
            List<NutritionEvaluationCollector.FoodNutritionEvaluation> nutritionEvaluations
    ) {

        return nutritionEvaluations.stream()
                .collect(
                        Collectors.groupingBy(
                                NutritionEvaluationCollector.FoodNutritionEvaluation::foodName,
                                Collectors.mapping(
                                        NutritionEvaluationCollector.FoodNutritionEvaluation::result,
                                        Collectors.toList()
                                )
                        )
                );
    }

    // AI 응답의 tags가 null인 경우(빈 배열 대신 null을 반환한 경우)를 대비한 안전 접근
    private static Set<RecommendationTag> nullSafeTags(
            CreatePlanAiResponse.PlanScheduleDetail schedule
    ) {

        return schedule.tags() == null ? Set.of() : schedule.tags();
    }

    private static String firstNonBlank(String candidate, String fallback) {

        return isBlank(candidate) ? fallback : candidate;
    }

    private static boolean isBlank(String value) {

        return value == null || value.isBlank();
    }

    // 여행 전체 기간이 이어지는 동안 직전 확정 장소를 유지하며 누락된 이동시간을 채움
    private CreatePlanAiResponse fillMissingTravelMinutes(
            CreatePlanAiResponse response,
            CreateTravelRequest createTravelRequest
    ) {

        List<CreatePlanAiResponse.PlanDayDetail> filledPlanDays = new ArrayList<>();
        String previousLocation = createTravelRequest.decidedLocation();
        CreatePlanAiResponse.PlanScheduleDetail previousPlace = null;

        for (CreatePlanAiResponse.PlanDayDetail planDay : response.planDays()) {
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules = new ArrayList<>();

            for (CreatePlanAiResponse.PlanScheduleDetail schedule : planDay.schedules()) {
                CreatePlanAiResponse.PlanScheduleDetail filled = fillScheduleTravelMinutes(
                        schedule,
                        previousLocation,
                        previousPlace,
                        createTravelRequest.transportation()
                );

                schedules.add(filled);

                if (filled.courseType() != CourseType.MEDICATION
                        && filled.courseType() != CourseType.TRANSPORTATION
                        && !isBlank(filled.locationName())) {
                    previousLocation = filled.locationName();
                    previousPlace = filled;
                }
            }

            filledPlanDays.add(
                    new CreatePlanAiResponse.PlanDayDetail(
                            planDay.dayNumber(),
                            planDay.date(),
                            schedules
                    )
            );
        }

        return new CreatePlanAiResponse(filledPlanDays);
    }

    // travelMinutes가 비어 있고 실제 장소(locationName)와 직전 장소가 모두 있는 슬롯만 대상
    // (MEDICATION처럼 장소가 없는 슬롯, 이미 값이 채워진 슬롯은 자동으로 제외됨)
    private CreatePlanAiResponse.PlanScheduleDetail fillScheduleTravelMinutes(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            String previousLocation,
            CreatePlanAiResponse.PlanScheduleDetail previousPlace,
            Transportation transportation
    ) {

        boolean needsTravelMinutes =
                schedule.travelMinutes() == null
                        && !isBlank(schedule.locationName())
                        && !isBlank(previousLocation);

        if (!needsTravelMinutes) {
            return schedule;
        }

        KakaoRouteResult route = lookupRoute(previousLocation, previousPlace, schedule, transportation);

        Integer travelMinutes =
                route == null ? null : route.travelMinutes();

        return new CreatePlanAiResponse.PlanScheduleDetail(
                schedule.scheduleType(),
                schedule.courseType(),
                schedule.startTime(),
                schedule.endTime(),
                schedule.locationName(),
                schedule.location(),
                schedule.longitude(),
                schedule.latitude(),
                schedule.imageUrl(),
                schedule.thumbNailImageUrl(),
                schedule.stayMinutes(),
                travelMinutes,
                schedule.tags(),
                schedule.medication(),
                schedule.restaurantDetail(),
                schedule.candidateId()
        );
    }

    private KakaoRouteResult lookupRoute(
            String previousLocation,
            CreatePlanAiResponse.PlanScheduleDetail previousPlace,
            CreatePlanAiResponse.PlanScheduleDetail destination,
            Transportation transportation
    ) {

        return kakaoMapServiceHandler
                .getRoute(
                        previousLocation,
                        destination.locationName(),
                        transportation,
                        previousPlace == null ? null : previousPlace.longitude(),
                        previousPlace == null ? null : previousPlace.latitude(),
                        destination.longitude(),
                        destination.latitude())
                .block();
    }

    /*
    기본 CRUD 모음
     */
    public void savePlan(Plan plan){

        planRepository.save(plan);
    }
}
