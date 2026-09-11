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
import com.planb.domain.travel.helper.PlanEditValidator;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.prompt.PlaceReselectPrompt;
import com.planb.domain.health.entity.constant.FoodType;
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
import com.planb.domain.travel.policy.TouristPlaceCountPolicy;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.helper.PlanPlaceResolver.Validation;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.domain.travel.repository.PlanRepository;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;

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

    /*
    Repository
     */
    private final PlanRepository planRepository;

    /*
    장소 확정, 수정 반영 검증, 시간표 계산
     */
    private final PlanPlaceResolver planPlaceResolver;

    private final PlanEditValidator planEditValidator;

    private final ScheduleNormalizer scheduleNormalizer;

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

        return finishPlan(validated, context, evaluations,
                RouteAnchor.from(context.createTravelRequest().decidedLocation()));
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

            rebuildDays = planEditValidator.rebuildDays(scope, context);

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

        CreatePlanAiResponse toFinish = !preserveOtherDays ? validated
                : new CreatePlanAiResponse(finishTargets(validated, rebuildDays));

        CreatePlanAiResponse finished = finishPlan(toFinish, travelContext, evaluations,
                preserveOtherDays
                        ? rebuildAnchor(context, rebuildDays)
                        : RouteAnchor.from(context.createTravelRequest().decidedLocation()));

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
                    .orElseThrow(() -> planEditValidator.failure("대상 날짜 누락"));

            String reason = "전체 재구성 요청에도 새로운 장소가 없는 " + dayNumber + "일차";

            for (int attempt = 0; !planEditValidator.rebuilt(context, target) && attempt < 2; attempt++) {
                PlaceCandidateContext candidates = new PlaceCandidateContext();

                RebuildPlanDayResponse rebuildResponse = travelRecommendHandler
                        .rebuildDay(
                                context,
                                current,
                                dayNumber,
                                reason,
                                candidates);

                Optional<String> responseFailure = planEditValidator
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
                        .forEach(slot -> planPlaceResolver.track(slot, places, menus));

                CreatePlanAiResponse checked;

                try {
                    checked = validatePlaces(replacement, candidates,
                            new TravelPlanContext(context.createTravelRequest(), context.healthContexts()),
                            context.currentPlan(), places, menus, rebuildAnchor(context, Set.of(dayNumber)));
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

                if (!planEditValidator.rebuilt(context, target)) {
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

            if (!planEditValidator.rebuilt(context, target)) {
                throw planEditValidator.failure(reason);
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
                Validation validation = planPlaceResolver.verifyExisting(slot, places, menus);

                if (!validation.valid()) {
                    throw invalidPlace(validation.reason());
                }

                schedules.add(validation.schedule());

                planPlaceResolver.track(validation.schedule(), places, menus);
            }

            preserved.add(new CreatePlanAiResponse.PlanDayDetail(day.dayNumber(), day.date(), schedules));
        }

        List<CreatePlanAiResponse.PlanDayDetail> targets = rebuildDays.stream().sorted().map(number -> {
            List<CreatePlanAiResponse.PlanDayDetail> matches = response.planDays().stream()
                    .filter(day -> day != null && Objects.equals(day.dayNumber(), number)).toList();

            CreatePlanAiResponse single = new CreatePlanAiResponse(matches);

            if (!planEditValidator.sameDay(context, number, single)) {
                throw planEditValidator.failure("재구성 대상 일차/날짜 불일치");
            }

            return matches.getFirst();
        }).toList();

        CreatePlanAiResponse checked = validatePlaces(new CreatePlanAiResponse(targets), candidates,
                new TravelPlanContext(context.createTravelRequest(), context.healthContexts()),
                context.currentPlan(), places, menus, rebuildAnchor(context, rebuildDays));

        return new CreatePlanAiResponse(Stream.concat(preserved.stream(), checked.planDays().stream())
                .sorted(Comparator.comparing(CreatePlanAiResponse.PlanDayDetail::dayNumber)).toList());
    }

    // 재구성 구간과 그 직후 보존 날짜를 함께 계산 대상으로 삼는다
    // 직후 날짜의 첫 이동시간은 재구성 전 장소를 기준으로 계산된 값이라 그대로 두면 어긋난다
    private List<CreatePlanAiResponse.PlanDayDetail> finishTargets(
            CreatePlanAiResponse validated,
            Set<Integer> rebuildDays
    ) {

        int trailing = rebuildDays
                .stream()
                .max(Integer::compareTo)
                .orElse(0) + 1;

        return validated
                .planDays()
                .stream()
                .filter(day -> rebuildDays.contains(day.dayNumber())
                        || Objects.equals(day.dayNumber(), trailing))
                .map(day -> rebuildDays.contains(day.dayNumber())
                        ? day
                        : clearFirstTravelMinutes(day))
                .toList();
    }

    // 직후 보존 날짜의 첫 장소만 이동시간을 비워 재계산 대상으로 만든다
    private CreatePlanAiResponse.PlanDayDetail clearFirstTravelMinutes(
            CreatePlanAiResponse.PlanDayDetail day
    ) {

        boolean cleared = false;

        List<CreatePlanAiResponse.PlanScheduleDetail> schedules = new ArrayList<>();

        for (CreatePlanAiResponse.PlanScheduleDetail slot : day.schedules()) {
            if (!cleared && planPlaceResolver.requiresPlace(slot)) {
                cleared = true;

                schedules.add(withTravelMinutes(slot, null));

                continue;
            }

            schedules.add(slot);
        }

        return new CreatePlanAiResponse.PlanDayDetail(
                day.dayNumber(),
                day.date(),
                schedules
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail withTravelMinutes(
            CreatePlanAiResponse.PlanScheduleDetail slot,
            Integer travelMinutes
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                slot.scheduleType(),
                slot.courseType(),
                slot.startTime(),
                slot.endTime(),
                slot.locationName(),
                slot.location(),
                slot.longitude(),
                slot.latitude(),
                slot.imageUrl(),
                slot.thumbNailImageUrl(),
                slot.stayMinutes(),
                travelMinutes,
                slot.tags(),
                slot.medication(),
                slot.restaurantDetail(),
                slot.candidateId()
        );
    }


    // 재구성 구간에서 가장 빠른 날짜의 직전 날짜를 이동시간 기준점으로 삼는다
    private RouteAnchor rebuildAnchor(
            PlanEditContext context,
            Set<Integer> rebuildDays
    ) {

        String decidedLocation = context.createTravelRequest().decidedLocation();

        return rebuildDays
                .stream()
                .min(Integer::compareTo)
                .map(first -> context.currentPlan()
                        .planDays()
                        .stream()
                        .filter(day -> Objects.equals(day.dayNumber(), first - 1))
                        .findFirst()
                        .map(previousDay -> RouteAnchor.after(previousDay, decidedLocation))
                        .orElseGet(() -> RouteAnchor.from(decidedLocation)))
                .orElseGet(() -> RouteAnchor.from(decidedLocation));
    }


    // 확정 장소와 시간에 따른 복약·태그·누락 이동시간 보정
    private CreatePlanAiResponse finishPlan(
            CreatePlanAiResponse response,
            TravelPlanContext context,
            List<NutritionEvaluationCollector.FoodNutritionEvaluation> evaluations,
            RouteAnchor anchor
    ) {

        // 복약 일정은 이동시간과 시간표가 확정된 뒤 한 번만 생성한다.
        // ScheduleNormalizer가 멱등이므로 이 시점의 선행 호출은 결과에 기여하지 않는다.
        CreatePlanAiResponse travelFixed = fillMissingTravelMinutes(
                response,
                context.createTravelRequest(),
                anchor
        );

        validateTravelMinutes(travelFixed);

        CreatePlanAiResponse normalized = scheduleNormalizer.normalizeScheduleTimes(
                travelFixed,
                context.healthContexts()
        );

        CreatePlanAiResponse medicationFixed = scheduleNormalizer.ensureMedicationSchedules(
                normalized,
                context.healthContexts()
        );

        CreatePlanAiResponse tagged = applyDeterministicTags(
                medicationFixed,
                context,
                evaluations
        );

        scheduleNormalizer.validateMealTimes(
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

        return validatePlaces(response, candidates, context, existing, new HashSet<>(), new HashSet<>(),
                RouteAnchor.from(context.createTravelRequest().decidedLocation()));
    }

    // 다른 날짜의 예약 장소·메뉴를 포함한 장소 검증
    // anchor는 이 응답 앞에 잘려나간 구간의 마지막 장소로, 일정 전체를 검증할 때는 여행 출발지
    private CreatePlanAiResponse validatePlaces(
            CreatePlanAiResponse response,
            PlaceCandidateContext candidates,
            TravelPlanContext context,
            GetAiPlanResponse existing,
            Set<String> usedPlaces,
            Set<String> usedMenus,
            RouteAnchor anchor
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

        response = scheduleNormalizer.normalizeScheduleTimes(
                response,
                context.healthContexts()
        );

        response = scheduleNormalizer.ensureMedicationSchedules(
                response,
                context.healthContexts()
        );

        scheduleNormalizer.validateMealTimes(
                response,
                context.healthContexts()
        );

        List<Validation> validations = new ArrayList<>();

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            if (day == null || day.schedules() == null || day.schedules().isEmpty()) {
                throw invalidPlace("일정 슬롯 누락");
            }

            for (CreatePlanAiResponse.PlanScheduleDetail slot : day.schedules()) {
                Validation validation = planPlaceResolver.validate(slot, candidates, usedPlaces, usedMenus);

                validations.add(validation);

                if (validation.valid()) {
                    planPlaceResolver.track(validation.schedule(), usedPlaces, usedMenus);
                }
            }
        }
        // 뒤쪽 정상 슬롯까지 예약하여 앞쪽 실패 슬롯의 재선택 대상에서 제외
        int validationIndex = 0;

        List<CreatePlanAiResponse.PlanDayDetail> days = new ArrayList<>();
        String previousLocation = anchor.previousLocation();
        CreatePlanAiResponse.PlanScheduleDetail previousPlace = anchor.previousPlace();

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

                if (changedRoute && planPlaceResolver.requiresPlace(resolved)) {
                    resolved = recalculateSlot(resolved, previousLocation, previousPlace, previousEnd, context.createTravelRequest());
                }

                schedules.add(resolved);

                planPlaceResolver.track(resolved, usedPlaces, usedMenus);

                if (planPlaceResolver.requiresPlace(resolved)) {
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

        int expectedCount = TouristPlaceCountPolicy.expectedCount(healthContexts);

        if (expectedCount == 0) {
            return;
        }

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
                .filter(planPlaceResolver::requiresPlace)
                .anyMatch(schedule -> schedule.travelMinutes() == null
                        || schedule.travelMinutes() < 0);

        if (invalid) {
            throw invalidPlace("이동시간 누락 또는 유효하지 않음");
        }
    }

    // 편집 전 동일 슬롯과 장소 비교를 통한 기존 이동시간 무효화
    private boolean changedFromExisting(
            GetAiPlanResponse existing,
            CreatePlanAiResponse.PlanDayDetail day,
            CreatePlanAiResponse.PlanScheduleDetail slot
    ) {

        if (existing == null || !planPlaceResolver.requiresPlace(slot)) {
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

            result = planPlaceResolver.validate(planPlaceResolver.select(slot, choice), retryCandidates, usedPlaces, usedMenus);

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
                .map(old -> planPlaceResolver.verifyExisting(old, usedPlaces, usedMenus))
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
            CreateTravelRequest createTravelRequest,
            RouteAnchor anchor
    ) {

        List<CreatePlanAiResponse.PlanDayDetail> filledPlanDays = new ArrayList<>();
        String previousLocation = anchor.previousLocation();
        CreatePlanAiResponse.PlanScheduleDetail previousPlace = anchor.previousPlace();

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
