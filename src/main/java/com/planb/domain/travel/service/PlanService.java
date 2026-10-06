package com.planb.domain.travel.service;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.ai.dto.response.PlanEditScope;
import com.planb.domain.travel.helper.PlanEditValidator;
import com.planb.ai.client.AiCorrectionTracker;
import com.planb.ai.handler.MissingSlotCompleter;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.prompt.PlaceReselectPrompt;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.dto.request.CreatePlanRequest;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.entity.constant.NutritionLevel;
import com.planb.domain.travel.entity.constant.NutritionType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.policy.MealSlotPolicy;
import com.planb.domain.travel.policy.TouristPlaceCountPolicy;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.helper.PlanPlaceResolver.Validation;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.domain.travel.repository.PlanRepository;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PlanService {

    public EditPlanPreviewResponse createEditPreviewResponse(
            GetAiPlanResponse currentPlan,
            EditPlanAiResponse editResponse
    ) {

        return new EditPlanPreviewResponse(currentPlan, editResponse);
    }

    public EditPlanPreviewResponse createEditPreviewResponse(
            PlanEditContext context,
            EditPlanAiResponse editResponse
    ) {

        return createEditPreviewResponse(context.currentPlan(), editResponse);
    }

    // 실제 근거가 있는 NutritionType만 RecommendationTag로 매핑 (평가는 되지만 대응 태그가 없는 타입 제외)
    private static final Map<NutritionType, RecommendationTag> NUTRITION_REFERENCE_TAGS = Map.of(
            NutritionType.CARBOHYDRATE,
            RecommendationTag.CARBOHYDRATE_REFERENCE,
            NutritionType.SODIUM,
            RecommendationTag.SODIUM_REFERENCE,
            NutritionType.SATURATED_FAT,
            RecommendationTag.SATURATED_FAT_REFERENCE
    );

    // 요청당 누락 메뉴 영양 조회 동시성 상한. 공급자 전역 상한과 별개
    @Value("${planb.travel.nutrition.lookup-concurrency:2}")
    private int nutritionLookupConcurrency = 2;

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

    // AI가 비운 이동시간의 Java 확정 (병렬·요청 범위 메모이즈·근거리 도보 추정)
    private final TravelMinutesResolver travelMinutesResolver;

    /*
    Tool 호출 결과 수집기
     */
    private final NutritionEvaluationCollector nutritionEvaluationCollector;

    private final NutritionService nutritionService;

    private final MissingSlotCompleter missingSlotCompleter;

    private final MeterRegistry meterRegistry;

    public Plan createPlan(CreatePlanRequest createPlanRequest) {

        return Plan
                .builder()
                .planName(createPlanRequest
                        .planName())
                .travel(createPlanRequest
                        .travel())
                .build();
    }

    public CreatePlanAiResponse makePlanByAi(
            CreateTravelRequest request,
            List<TravelHealthContext> healthContexts
    ) {

        return makePlanByAi(new TravelPlanContext(request, healthContexts));
    }

    // AI 일정 생성 및 검색 원본 기반 슬롯 확정
    public CreatePlanAiResponse makePlanByAi(TravelPlanContext context) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "failure";
        String corrected = "false";

        AiCorrectionTracker.start();

        try {
            nutritionEvaluationCollector.start();

            CreatePlanAiResponse validated;

            List<NutritionEvaluationCollector.FoodNutritionEvaluation> evaluations;

            // finishPlan 식사 재보정을 위한 호출 단위 후보 유지
            PlaceCandidateContext candidates = new PlaceCandidateContext();

            CreatePlanAiResponse response;

            try {
                response = recordStage(
                        "create",
                        "ai_response",
                        () -> travelRecommendHandler.createPlanByAi(context, candidates)
                );

                CreatePlanAiResponse aiResponse = response;

                validated = recordStage(
                        "create",
                        "place_validation",
                        () -> validatePlaces(
                                aiResponse,
                                candidates,
                                context,
                                null
                        )
                );
            } finally {
                evaluations = nutritionEvaluationCollector.finish();
            }

            CreatePlanAiResponse result = recordStage(
                    "create",
                    "finish_plan",
                    () -> finishPlan(
                            validated,
                            context,
                            evaluations,
                            RouteAnchor.from(context
                                            .createTravelRequest()
                                            .decidedLocation()),
                            candidates,
                            Set.of(),
                            null
                    )
            );

            outcome = "success";

            return result;
        } finally {
            corrected = Boolean.toString(AiCorrectionTracker.finish());

            sample.stop(
                    Timer
                            .builder("planb.travel.ai.orchestration")
                            .tag(
                                    "days",
                                    Integer.toString(
                                            context
                                                    .createTravelRequest()
                                                    .dateType()
                                                    .getPlusDays() + 1
                                    )
                            )
                            .tag("outcome", outcome)
                            .tag("corrected", corrected)
                            .register(meterRegistry)
            );
        }
    }

    private <T> T recordStage(
            String flow,
            String stage,
            Supplier<T> operation
    ) {

        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "failure";

        try {
            T result = operation.get();
            outcome = "success";

            return result;
        } finally {
            sample.stop(stageTimer(
                    flow,
                    stage,
                    outcome
            ));
        }
    }

    // AI 일정 수정 및 검증된 기존 슬롯 복구
    public EditPlanAiResponse makeEditPlanByAi(PlanEditContext context) {

        nutritionEvaluationCollector.start();

        EditPlanAiResponse response;

        CreatePlanAiResponse validated;

        Set<Integer> rebuildDays;

        Set<Integer> densityReductionDays;

        boolean preserveOtherDays;

        TravelPlanContext travelContext = new TravelPlanContext(context.createTravelRequest(), context.healthContexts());

        List<NutritionEvaluationCollector.FoodNutritionEvaluation> evaluations;

        // finishPlan 식사 재보정을 위한 호출 단위 후보 유지
        PlaceCandidateContext candidates = new PlaceCandidateContext();

        try {
            PlanEditScope scope = travelRecommendHandler.classifyEditScope(context);

            rebuildDays = planEditValidator.rebuildDays(scope, context);

            densityReductionDays = planEditValidator.densityReductionDays(
                    scope,
                    context
            );

            preserveOtherDays = !rebuildDays.isEmpty() && scope.preserveOtherDays();

            response = travelRecommendHandler.editPlanByAi(context, candidates);

            CreatePlanAiResponse proposed = new CreatePlanAiResponse(response.planDays());

            validated = preserveOtherDays
                    ? validateRebuildTargets(
                            context,
                            proposed,
                            candidates,
                            rebuildDays,
                            densityReductionDays
                    )
                    : validatePlaces(
                            proposed,
                            candidates,
                            travelContext,
                            context.currentPlan(),
                            densityReductionDays
                    );

            validated = ensureDaysRebuilt(
                    context,
                    validated,
                    rebuildDays,
                    densityReductionDays
            );
        } finally {
            evaluations = nutritionEvaluationCollector.finish();
        }

        // 편집 전 조회로 확정된 영양 수치 보존
        // 재구성 날짜 직후 날짜는 첫 이동시간을 다시 계산해야 해서 finishPlan을 함께 거치는데,
        // 재평가하지 않은 날짜의 빈 영양 조회 결과
        // 이전에 저장된 영양 수치 소실 방지
        // 이번 조회 우선 적용과 누락 메뉴의 저장 수치 복원
        evaluations = Stream
                .concat(
                        evaluations.stream(),
                        storedNutritionEvaluations(context.currentPlan())
                                .stream()
                )
                .toList();

        CreatePlanAiResponse toFinish = !preserveOtherDays ? validated
                : new CreatePlanAiResponse(finishTargets(validated, rebuildDays));

        CreatePlanAiResponse finished = finishPlan(
                toFinish,
                travelContext,
                evaluations,
                preserveOtherDays
                        ? rebuildAnchor(context, rebuildDays)
                        : RouteAnchor.from(context
                                        .createTravelRequest()
                                        .decidedLocation()),
                candidates,
                densityReductionDays,
                context.currentPlan()
        );

        CreatePlanAiResponse result = !preserveOtherDays
                ? finished
                : new CreatePlanAiResponse(
                        validated
                                .planDays()
                                .stream()
                                .map(day -> finished
                                        .planDays()
                                        .stream()
                                        .filter(updated -> Objects.equals(
                                                updated.dayNumber(),
                                                day.dayNumber()
                                        ))
                                        .findFirst()
                                        .orElse(day))
                                .toList()
                );

        List<String> changes = rebuildDays.isEmpty()
                ? response.changes()
                : Stream
                        .concat(
                        preserveOtherDays
                                ? Stream.<String>empty()
                                : response
                                        .changes()
                                        .stream(),
                        rebuildDays
                                .stream()
                                .sorted()
                                .map(day -> day + "일차 장소 구성 재구성")
                )
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
            Set<Integer> rebuildDays,
            Set<Integer> densityReductionDays
    ) {

        CreatePlanAiResponse current = response;

        for (Integer dayNumber : rebuildDays
                .stream()
                .sorted()
                .toList()) {
            CreatePlanAiResponse.PlanDayDetail target = current
                    .planDays()
                    .stream()
                    .filter(day -> Objects.equals(day.dayNumber(), dayNumber))
                    .findFirst()
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
                        candidates
                );

                Optional<String> responseFailure = planEditValidator
                        .rebuildFailure(
                        context,
                        dayNumber,
                        rebuildResponse
                );

                if (responseFailure.isPresent()) {
                    reason = responseFailure.get();

                    log.warn(
                            "[AI DAY REBUILD] attempt={}, targetDay={}, reason={}",
                            attempt + 1,
                            dayNumber,
                            reason
                    );

                    continue;
                }

                CreatePlanAiResponse replacement = new CreatePlanAiResponse(rebuildResponse.planDays());

                Set<String> places = new HashSet<>();

                Set<String> menus = new HashSet<>();

                current
                        .planDays()
                        .stream()
                        .filter(day -> !Objects.equals(day.dayNumber(), dayNumber))
                        .flatMap(day -> day
                                .schedules()
                                .stream())
                        .forEach(slot -> planPlaceResolver.track(
                                slot,
                                places,
                                menus
                        ));

                CreatePlanAiResponse checked;

                try {
                    checked = validatePlaces(
                            replacement,
                            candidates,
                            new TravelPlanContext(
                                    context.createTravelRequest(),
                                    context.healthContexts()
                            ),
                            context.currentPlan(),
                            places,
                            menus,
                            densityReductionDays,
                            rebuildAnchor(
                                    context,
                                    Set.of(dayNumber)
                            )
                    );
                } catch (BaseException exception) {
                    if (!PlanEditExceptionEnum.INVALID_AI_PLACE
                            .getCode()
                            .equals(exception.getErrorCode())) {
                        throw exception;
                    }

                    reason = exception.getMessage();

                    log.warn(
                            "[AI DAY REBUILD] attempt={}, targetDay={}, reason={}",
                            attempt + 1,
                            dayNumber,
                            reason
                    );

                    continue;
                }

                target = checked
                        .planDays()
                        .getFirst();

                reason = "원본 검증 후에도 새로운 장소가 없는 " + dayNumber + "일차";

                if (!planEditValidator.rebuilt(context, target)) {
                    log.warn(
                            "[AI DAY REBUILD] attempt={}, targetDay={}, reason={}",
                            attempt + 1,
                            dayNumber,
                            reason
                    );
                }

                CreatePlanAiResponse.PlanDayDetail rebuilt = target;

                current = new CreatePlanAiResponse(
                        current
                                .planDays()
                                .stream()
                                .map(day -> Objects.equals(day.dayNumber(), dayNumber)
                                        ? rebuilt
                                        : day)
                                .toList()
                );
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
            Set<Integer> rebuildDays,
            Set<Integer> densityReductionDays
    ) {

        Set<String> places = new HashSet<>();

        Set<String> menus = new HashSet<>();

        List<CreatePlanAiResponse.PlanDayDetail> preserved = new ArrayList<>();

        for (GetAiPlanResponse.PlanDayDetail day : context
                .currentPlan()
                .planDays()) {
            if (rebuildDays.contains(day.dayNumber())) {
                continue;
            }

            List<CreatePlanAiResponse.PlanScheduleDetail> schedules = new ArrayList<>();

            for (GetAiPlanResponse.PlanScheduleDetail slot : day.schedules()) {
                Validation validation = planPlaceResolver.verifyExisting(
                        slot,
                        places,
                        menus
                );

                // 기존 확정 일정인 보존 날짜의 검증 실패로 편집 차단 방지
                // 저장 데이터 구조 결함의 로그 기록
                if (!validation.valid()) {
                    log.warn(
                            "[PRESERVED SLOT] dayNumber={}, locationName={}, reason={}",
                            day.dayNumber(),
                            slot.locationName(),
                            validation.reason()
                    );
                }

                CreatePlanAiResponse.PlanScheduleDetail preservedSlot = validation.valid()
                        ? validation.schedule()
                        : planPlaceResolver.fromExisting(slot);

                schedules.add(preservedSlot);

                planPlaceResolver.track(
                        preservedSlot,
                        places,
                        menus
                );
            }

            preserved.add(new CreatePlanAiResponse.PlanDayDetail(
                            day.dayNumber(),
                            day.date(),
                            schedules
                    ));
        }

        List<CreatePlanAiResponse.PlanDayDetail> targets = rebuildDays
                .stream()
                .sorted()
                .map(number -> {
                    List<CreatePlanAiResponse.PlanDayDetail> matches = response
                            .planDays()
                            .stream()
                            .filter(day -> day != null
                                    && Objects.equals(day.dayNumber(), number))
                            .toList();

                    CreatePlanAiResponse single = new CreatePlanAiResponse(matches);

                    if (!planEditValidator.sameDay(
                            context,
                            number,
                            single
                    )) {
                        throw planEditValidator.failure("재구성 대상 일차/날짜 불일치");
                    }

                    return matches.getFirst();
                })
                .toList();

        CreatePlanAiResponse checked = validatePlaces(
                new CreatePlanAiResponse(targets),
                candidates,
                new TravelPlanContext(
                        context.createTravelRequest(),
                        context.healthContexts()
                ),
                context.currentPlan(),
                places,
                menus,
                densityReductionDays,
                rebuildAnchor(
                        context,
                        rebuildDays
                )
        );

        return new CreatePlanAiResponse(
                Stream
                        .concat(
                                preserved.stream(),
                                checked
                                        .planDays()
                                        .stream()
                        )
                        .sorted(Comparator.comparing(CreatePlanAiResponse.PlanDayDetail::dayNumber))
                        .toList()
        );
    }

    // 재구성 구간과 직후 보존 날짜의 동시 재계산
    // 재구성 전 장소 기준 이동시간의 정합성 보정
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

    // 직후 보존 날짜 첫 장소의 이동시간만 재계산
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


    // 재구성 첫 날짜의 직전 날짜를 이동시간 기준점으로 선택
    private RouteAnchor rebuildAnchor(
            PlanEditContext context,
            Set<Integer> rebuildDays
    ) {

        String decidedLocation = context
                .createTravelRequest()
                .decidedLocation();

        return rebuildDays
                .stream()
                .min(Integer::compareTo)
                .map(first -> context
                        .currentPlan()
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
            RouteAnchor anchor,
            PlaceCandidateContext candidates,
            Set<Integer> densityReductionDays,
            GetAiPlanResponse currentPlan
    ) {

        // 이동시간·시간표 확정 후 단일 복약 일정 생성
        // 멱등인 ScheduleNormalizer 선행 호출 제외
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

        // 생성·편집 공통 finish 경계의 등록 식사 재보정
        // 이미 완성된 일정 유지와 필수 식사 누락 시 후속 검증 거부
        CreatePlanAiResponse mealFixed = refillMissingMeals(
                normalized,
                context,
                anchor,
                candidates,
                densityReductionDays
        );

        validateTravelMinutes(mealFixed);

        validateMealSlots(
                mealFixed,
                context.healthContexts(),
                currentPlan,
                context
                        .createTravelRequest()
                        .dateType()
                        .getPlusDays() + 1
        );

        String flow = currentPlan == null ? "create" : "edit";

        List<NutritionEvaluationCollector.FoodNutritionEvaluation> finalEvaluations =
                recordStage(
                        flow,
                        "nutrition_enrichment",
                        () -> enrichMissingNutritionEvaluations(
                                mealFixed,
                                context.healthContexts(),
                                evaluations,
                                flow
                        )
                );

        // 식사 슬롯 확정 후 식전·식후 복약 배치
        CreatePlanAiResponse medicationFixed = scheduleNormalizer.ensureMedicationSchedules(
                mealFixed,
                context.healthContexts()
        );

        CreatePlanAiResponse tagged = applyDeterministicTags(
                medicationFixed,
                context,
                finalEvaluations
        );

        return tagged;
    }

    // 최종 일정에서 기존 영양평가가 없는 메뉴만 추가
    private List<NutritionEvaluationCollector.FoodNutritionEvaluation> enrichMissingNutritionEvaluations(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            List<NutritionEvaluationCollector.FoodNutritionEvaluation> evaluations,
            String flow
    ) {

        List<NutritionEvaluationCollector.FoodNutritionEvaluation> enriched =
                new ArrayList<>(evaluations);

        Set<String> evaluatedMenus = evaluations
                .stream()
                .map(NutritionEvaluationCollector.FoodNutritionEvaluation::foodName)
                .filter(menuName -> !isBlank(menuName))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<DiseaseType> diseaseTypes = healthContexts
                .stream()
                .filter(Objects::nonNull)
                .flatMap(healthContext -> healthContext.diseaseTypes() == null
                        ? Stream.empty()
                        : healthContext
                                .diseaseTypes()
                                .stream())
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();

        // 조회 전 중복 제거로 메뉴 목록 확정
        List<String> missingMenus = response
                .planDays()
                .stream()
                .flatMap(planDay -> planDay
                        .schedules()
                        .stream())
                .map(CreatePlanAiResponse.PlanScheduleDetail::restaurantDetail)
                .filter(Objects::nonNull)
                .map(CreatePlanAiResponse.RestaurantDetail::menuName)
                .filter(menuName -> !isBlank(menuName))
                .filter(evaluatedMenus::add)
                .toList();

        // 동시 조회 결과의 기존 메뉴 순서 보존
        enriched.addAll(Flux
                .fromIterable(missingMenus)
                .flatMapSequential(
                        menuName -> lookupNutrition(
                                menuName,
                                diseaseTypes,
                                flow
                        ),
                        nutritionLookupConcurrency
                )
                .collectList()
                .block());

        return enriched;
    }

    // 메뉴 1건 조회와 구독 시작부터 종료까지의 단계 시간
    private Mono<NutritionEvaluationCollector.FoodNutritionEvaluation> lookupNutrition(
            String menuName,
            List<DiseaseType> diseaseTypes,
            String flow
    ) {

        return Mono.defer(() -> {

            Timer.Sample sample = Timer.start(meterRegistry);

            // 하류 전달 전 결과 확정과 다른 메뉴 실패로 인한 취소 구분
            AtomicReference<String> outcome = new AtomicReference<>("cancelled");

            // 조회 호출의 동기 예외 기록
            return Mono
                    .defer(() -> nutritionService
                            .evaluateFoodNutrition(
                                    menuName,
                                    diseaseTypes
                            ))
                    .map(result -> new NutritionEvaluationCollector.FoodNutritionEvaluation(
                            menuName,
                            result
                    ))
                    .doOnSuccess(ignored -> outcome.set("success"))
                    .doOnError(ignored -> outcome.set("failure"))
                    .doFinally(ignored -> sample.stop(stageTimer(
                            flow,
                            "nutrition_lookup",
                            outcome.get()
                    )));
        });
    }

    private Timer stageTimer(
            String flow,
            String stage,
            String outcome
    ) {

        return Timer
                .builder("planb.travel.plan.stage")
                .tag("flow", flow)
                .tag("stage", stage)
                .tag("outcome", outcome)
                .register(meterRegistry);
    }

    /**
     * 최종 일정의 등록 식사 누락 보충
     */
    private CreatePlanAiResponse refillMissingMeals(
            CreatePlanAiResponse response,
            TravelPlanContext context,
            RouteAnchor anchor,
            PlaceCandidateContext candidates,
            Set<Integer> densityReductionDays
    ) {

        if (missingMealDays(response, context.healthContexts())
                .isEmpty()) {
            return response;
        }

        CreatePlanAiResponse filled = missingSlotCompleter.complete(
                response,
                context.healthContexts(),
                candidates,
                new HashSet<>(),
                new HashSet<>(),
                densityReductionDays,
                context
                        .createTravelRequest()
                        .dateType()
                        .getPlusDays() + 1
        );

        return scheduleNormalizer.normalizeScheduleTimes(
                fillMissingTravelMinutes(
                        filled,
                        context.createTravelRequest(),
                        anchor
                ),
                context.healthContexts()
        );
    }

    /**
     * 필수 식사 누락에 대한 최종 검증, 편집 전 일정 부재 시 면제 제외
     */
    private void validateMealSlots(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            GetAiPlanResponse currentPlan,
            int totalDays
    ) {

        List<String> failures = requiredMealSlotFailures(
                response,
                healthContexts,
                currentPlan,
                totalDays
        );

        if (!failures.isEmpty()) {
            log.info(
                    "[MEAL VALIDATION] 필수 식사 슬롯 누락 - failures: {}, schedules: {}",
                    failures,
                    response
                            .planDays()
                            .stream()
                            .map(day -> "day=" + day.dayNumber() + ":" + day
                                    .schedules()
                                    .stream()
                                    .map(schedule -> schedule.scheduleType() + "@" + schedule.startTime())
                                    .toList())
                            .toList()
            );

            throw invalidPlace("식사 슬롯 누락: " + failures);
        }
    }

    private List<String> requiredMealSlotFailures(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            GetAiPlanResponse currentPlan,
            int totalDays
    ) {

        Map<Integer, Set<ScheduleType>> alreadyMissing =
                baselineMissingMeals(currentPlan);

        return response
                .planDays()
                .stream()
                .flatMap(day -> MealSlotPolicy
                        .requiredMissingMeals(
                        day,
                        healthContexts,
                        totalDays
                )
                        .stream()
                        .filter(mealType -> !alreadyMissing
                                .getOrDefault(day.dayNumber(), Set.of())
                                .contains(mealType))
                        .map(mealType -> "day=" + day.dayNumber() + ", meal=" + mealType))
                .toList();
    }

    /**
     * 편집 전 일정의 날짜별 기존 식사 누락 조회
     * 새로 생긴 날짜는 면제하지 않는 검증
     */
    private Map<Integer, Set<ScheduleType>> baselineMissingMeals(
            GetAiPlanResponse currentPlan
    ) {

        if (currentPlan == null || currentPlan.planDays() == null) {
            return Map.of();
        }

        Map<Integer, Set<ScheduleType>> absentByDay = new HashMap<>();

        for (GetAiPlanResponse.PlanDayDetail day : currentPlan.planDays()) {

            Set<ScheduleType> present = day
                    .schedules()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(GetAiPlanResponse.PlanScheduleDetail::scheduleType)
                    .collect(Collectors.toSet());

            Set<ScheduleType> absent = MealSlotPolicy.MEAL_SCHEDULE_TYPES
                    .stream()
                    .filter(mealType -> !present.contains(mealType))
                    .collect(Collectors.toSet());

            if (!absent.isEmpty()) {
                absentByDay.put(
                        day.dayNumber(),
                        absent
                );
            }
        }

        return absentByDay;
    }

    // 식사 누락 날짜의 재보정 필요 여부 판정
    private List<Integer> missingMealDays(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        return response
                .planDays()
                .stream()
                .filter(day -> !MealSlotPolicy
                        .missingMeals(day, healthContexts)
                        .isEmpty())
                .map(CreatePlanAiResponse.PlanDayDetail::dayNumber)
                .toList();
    }

    // Plan 객체 단건 조회하기 (존재 검증은 호출부에서 이미 끝난 상태를 전제)
    public Plan findPlanById(Long planId) {

        return planRepository.getReferenceById(planId);
    }

    // planDays에 붙은 모든 RecommendationTag를 모아 Plan 전체 태그로 집계하기
    // CreatePlanAiResponse.PlanDayDetail을 EditPlanAiResponse도 재사용하므로 Create/Edit 공통 사용
    public Set<RecommendationTag> aggregateTags(
            List<CreatePlanAiResponse.PlanDayDetail> planDays
    ) {

        return planDays
                .stream()
                .flatMap(planDay -> planDay
                        .schedules()
                        .stream())
                .flatMap(schedule -> nullSafeTags(schedule)
                        .stream())
                .collect(Collectors.toSet());
    }

    // 검색 원본 검증 및 실패 슬롯별 제한 복구
    private CreatePlanAiResponse validatePlaces(
            CreatePlanAiResponse response,
            PlaceCandidateContext candidates,
            TravelPlanContext context,
            GetAiPlanResponse existing
    ) {

        return validatePlaces(
                response,
                candidates,
                context,
                existing,
                Set.of()
        );
    }

    private CreatePlanAiResponse validatePlaces(
            CreatePlanAiResponse response,
            PlaceCandidateContext candidates,
            TravelPlanContext context,
            GetAiPlanResponse existing,
            Set<Integer> densityReductionDays
    ) {

        return validatePlaces(
                response,
                candidates,
                context,
                existing,
                new HashSet<>(),
                new HashSet<>(),
                densityReductionDays,
                RouteAnchor.from(
                        context
                                .createTravelRequest()
                                .decidedLocation()
                )
        );
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
            Set<Integer> densityReductionDays,
            RouteAnchor anchor
    ) {

        if (response == null || response.planDays() == null || response
                .planDays()
                .isEmpty()) {
            throw invalidPlace("일정 누락");
        }

        validatePlanDays(
                response,
                context.createTravelRequest()
        );

        // 개수 검증 직전의 단일 빈 슬롯 보충·초과분 제거
        // 생성·편집·재구성 공통 보정 지점
        // usedPlaces·usedMenus에는 이 응답 밖 날짜의 장소와 메뉴가 들어있어,
        // 일부 날짜 검증 시에도 장소 중복 방지
        response = missingSlotCompleter.complete(
                response,
                context.healthContexts(),
                candidates,
                usedPlaces,
                usedMenus,
                densityReductionDays,
                context
                        .createTravelRequest()
                        .dateType()
                        .getPlusDays() + 1
        );

        int totalDays = context
                .createTravelRequest()
                .dateType()
                .getPlusDays() + 1;

        if (!requiredMealSlotFailures(
                response,
                context.healthContexts(),
                existing,
                totalDays
        )
                .isEmpty()) {
            travelRecommendHandler.collectRestaurantCandidates(
                    context,
                    candidates
            );

            response = missingSlotCompleter.complete(
                    response,
                    context.healthContexts(),
                    candidates,
                    usedPlaces,
                    usedMenus,
                    densityReductionDays,
                    totalDays
            );
        }

        response = TouristPlaceCountPolicy.trimExcess(
                response,
                context.healthContexts()
        );

        validateTouristPlaceCounts(
                response,
                context.healthContexts(),
                densityReductionDays
        );

        response = scheduleNormalizer.normalizeScheduleTimes(
                response,
                context.healthContexts()
        );

        response = scheduleNormalizer.ensureMedicationSchedules(
                response,
                context.healthContexts()
        );

        List<Validation> validations = new ArrayList<>();

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            if (day == null || day.schedules() == null || day
                    .schedules()
                    .isEmpty()) {
                throw invalidPlace("일정 슬롯 누락");
            }

            for (CreatePlanAiResponse.PlanScheduleDetail slot : day.schedules()) {
                Validation validation = planPlaceResolver.validate(
                        slot,
                        candidates,
                        usedPlaces,
                        usedMenus
                );

                validations.add(validation);

                if (validation.valid()) {
                    planPlaceResolver.track(
                            validation.schedule(),
                            usedPlaces,
                            usedMenus
                    );
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
                        slot,
                        validation,
                        day,
                        existing,
                        context,
                        usedPlaces,
                        usedMenus
                );

                boolean changed = !Objects.equals(slot.locationName(), resolved.locationName())
                        || !Objects.equals(slot.longitude(), resolved.longitude())
                        || !Objects.equals(slot.latitude(), resolved.latitude())
                        || changedFromExisting(
                                existing,
                                day,
                                resolved
                        );

                changedRoute = changedRoute || changed;

                if (changedRoute && planPlaceResolver.requiresPlace(resolved)) {
                    resolved = recalculateSlot(
                            resolved,
                            previousLocation,
                            previousPlace,
                            previousEnd,
                            context.createTravelRequest()
                    );
                }

                schedules.add(resolved);

                planPlaceResolver.track(
                        resolved,
                        usedPlaces,
                        usedMenus
                );

                if (planPlaceResolver.requiresPlace(resolved)) {
                    previousLocation = resolved.locationName();

                    previousPlace = resolved;

                    previousEnd = resolved.endTime();
                }
            }

            days.add(new CreatePlanAiResponse.PlanDayDetail(
                            day.dayNumber(),
                            day.date(),
                            schedules
                    ));
        }

        return new CreatePlanAiResponse(days);
    }

    private void validatePlanDays(
            CreatePlanAiResponse response,
            CreateTravelRequest request
    ) {

        Set<Integer> dayNumbers = new HashSet<>();
        int lastDayNumber = request
                .dateType()
                .getPlusDays() + 1;

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            if (day == null || day.dayNumber() == null || day.date() == null) {
                throw invalidPlace("일차 또는 날짜 누락");
            }

            if (day.dayNumber() < 1 || day.dayNumber() > lastDayNumber
                    || !dayNumbers.add(day.dayNumber())) {
                throw invalidPlace("유효하지 않거나 중복된 일차");
            }

            if (!day
                    .date()
                    .equals(request
                            .startDate()
                            .plusDays(day.dayNumber() - 1L))) {
                throw invalidPlace("일차와 날짜 불일치");
            }
        }
    }

    private void validateTouristPlaceCounts(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            Set<Integer> densityReductionDays
    ) {

        TouristPlaceCountPolicy
                .violations(
                        response,
                        healthContexts,
                        densityReductionDays
                )
                .stream()
                .findFirst()
                .ifPresent(violation -> {
                    String expectedCount = violation.minimumCount() == violation.maximumCount()
                            ? String.valueOf(violation.maximumCount())
                            : violation.minimumCount() + "~" + violation.maximumCount();

                    throw invalidPlace(
                            "관광 장소 개수 불일치: day=" + violation.dayNumber()
                                    + ", expected=" + expectedCount
                                    + ", actual=" + violation.actualCount()
                    );
                });
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

        return existing
                .planDays()
                .stream()
                .filter(oldDay -> Objects.equals(oldDay.date(), day.date())
                        && Objects.equals(oldDay.dayNumber(), day.dayNumber()))
                .flatMap(oldDay -> oldDay
                        .schedules()
                        .stream())
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

            String reason = result.reason();

            // 재선택 LLM 호출의 횟수·시간 계측, 기존 일정 유무로 생성·편집 구분
            CreatePlanAiResponse.PlanScheduleDetail choice = recordStage(
                    existing == null ? "create" : "edit",
                    "reselect",
                    () -> travelRecommendHandler.reselectPlace(
                            new PlaceReselectPrompt(
                                    context,
                                    slot,
                                    reason,
                                    Set.copyOf(usedPlaces),
                                    Set.copyOf(usedMenus)
                            ),
                            retryCandidates
                    )
            );

            result = planPlaceResolver.validate(
                    planPlaceResolver.select(slot, choice),
                    retryCandidates,
                    usedPlaces,
                    usedMenus
            );

            if (result.valid()) {
                return result.schedule();
            }
        }

        String finalReason = result.reason();

        return verifiedFallback(
                existing,
                day,
                slot,
                usedPlaces,
                usedMenus
        )
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

        return existing
                .planDays()
                .stream()
                .filter(oldDay -> Objects.equals(oldDay.date(), day.date())
                        && Objects.equals(oldDay.dayNumber(), day.dayNumber()))
                .flatMap(oldDay -> oldDay
                        .schedules()
                        .stream())
                .filter(old -> old.scheduleType() == slot.scheduleType() && old.courseType() == slot.courseType()
                        && Objects.equals(old.startTime(), slot.startTime()))
                .map(old -> planPlaceResolver.verifyExisting(
                        old,
                        usedPlaces,
                        usedMenus
                ))
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

        int travelMinutes = travelMinutesResolver
                .minutes(
                        previousLocation,
                        previousPlace,
                        slot,
                        request.transportation()
                )
                .orElseThrow(() -> invalidPlace("장소 변경 후 이동시간 확인 실패: origin=" + previousLocation
                        + ", destination=" + slot.locationName() + ", transportation=" + request.transportation()));

        LocalTime arrival = previousEnd == null ? slot.startTime() : previousEnd.plusMinutes(travelMinutes);

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
                travelMinutes,
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
                response
                        .planDays()
                        .stream()
                        .map(planDay ->
                                new CreatePlanAiResponse.PlanDayDetail(
                                        planDay.dayNumber(),
                                        planDay.date(),
                                        planDay
                                                .schedules()
                                                .stream()
                                                .map(schedule ->
                                                        addDeterministicTags(
                                                                schedule,
                                                                createTravelRequest,
                                                                resultsByFoodName,
                                                                hasAllergyOrAvoidFood,
                                                                travelPlanContext.healthContexts()
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
            boolean hasAllergyOrAvoidFood,
            List<TravelHealthContext> healthContexts
    ) {

        Set<RecommendationTag> deterministicTags =
                deterministicTagsFor(
                        schedule,
                        createTravelRequest,
                        resultsByFoodName,
                        hasAllergyOrAvoidFood
                );

        Set<RecommendationTag> mergedTags = new HashSet<>(nullSafeTags(schedule));

        mergedTags.addAll(deterministicTags);

        // AI 태그가 아닌 확정 시간표 기준의 식사시간 반영 판정
        if (scheduleNormalizer.mealSlot(schedule)) {
            if (scheduleNormalizer.mealTimeSatisfied(schedule, healthContexts)) {
                mergedTags.add(RecommendationTag.MEAL_TIME_APPLIED);
            } else {
                mergedTags.remove(RecommendationTag.MEAL_TIME_APPLIED);
            }
        }

        // 장소 유형에 맞지 않는 태그 제거
        // AI 응답의 CourseType별 태그 정책 불이행 대비
        mergedTags.retainAll(RecommendationTag.candidates(schedule.courseType()));

        CreatePlanAiResponse.RestaurantDetail restaurantDetail =
                nutritionAlignedRestaurant(
                        schedule.restaurantDetail(),
                        resultsByFoodName
                );

        if (mergedTags.equals(nullSafeTags(schedule))
                && restaurantDetail == schedule.restaurantDetail()) {

            return schedule;
        }

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
                restaurantDetail,
                schedule.candidateId()
        );
    }

    /**
     * Tool 조회로 확인된 메뉴 영양 수치만 보존
     */
    private CreatePlanAiResponse.RestaurantDetail nutritionAlignedRestaurant(
            CreatePlanAiResponse.RestaurantDetail restaurantDetail,
            Map<String, List<NutritionEvaluationResult>> resultsByFoodName
    ) {

        if (restaurantDetail == null) {
            return null;
        }

        NutritionEvaluationResult lookup = resultsByFoodName
                .getOrDefault(restaurantDetail.menuName(), List.of())
                .stream()
                .filter(result -> result.status() != NutritionEvaluationStatus.UNAVAILABLE)
                .findFirst()
                .orElse(null);

        if (lookup == null) {

            // 운영 중 빈 메뉴 비율 측정용 기록
            // 식당 고유 메뉴명은 식약처에 없어 조회되지 않는다. 그 비율이 높으면
            // AI 응답의 표준 품목명 분리 수집 검토
            log.info(
                    "영양성분 조회 실패 - menuName: {}",
                    restaurantDetail.menuName()
            );
        }

        Double carbohydrate = lookup == null ? null : lookup.carbohydrate();
        Double sodium = lookup == null ? null : lookup.sodium();
        Double fat = lookup == null ? null : lookup.fat();

        if (Objects.equals(carbohydrate, restaurantDetail.carbohydrate())
                && Objects.equals(sodium, restaurantDetail.sodium())
                && Objects.equals(fat, restaurantDetail.fat())) {

            return restaurantDetail;
        }

        return new CreatePlanAiResponse.RestaurantDetail(
                restaurantDetail.menuName(),
                carbohydrate,
                sodium,
                fat,
                restaurantDetail.openTime(),
                restaurantDetail.address(),
                restaurantDetail.longitude(),
                restaurantDetail.latitude(),
                restaurantDetail.imageUrl()
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

        return Stream
                .concat(
                        createTravelRequest
                                .localFoods()
                                .stream(),
                        createTravelRequest
                                .recommendFoods()
                                .stream()
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
                .flatMap(result -> result
                        .evaluations()
                        .stream())
                .filter(detail -> detail.nutritionLevel() != NutritionLevel.LOW)
                .map(detail -> NUTRITION_REFERENCE_TAGS.get(detail.nutritionType()))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    // 여행자 중 알레르기/기피 음식이 등록된 사람이 있는지 확인
    private boolean hasAllergyOrAvoidFood(List<TravelHealthContext> healthContexts) {

        return healthContexts
                .stream()
                .flatMap(healthContext -> healthContext
                        .foodInfos()
                        .stream())
                .anyMatch(foodInfo ->
                        foodInfo.foodType() == FoodType.ALLERGY
                                || foodInfo.foodType() == FoodType.AVOID
                );
    }

    /**
     * 저장된 메뉴 영양 수치의 조회 결과 복원
     */
    private List<NutritionEvaluationCollector.FoodNutritionEvaluation> storedNutritionEvaluations(
            GetAiPlanResponse currentPlan
    ) {

        if (currentPlan == null || currentPlan.planDays() == null) {
            return List.of();
        }

        return currentPlan
                .planDays()
                .stream()
                .flatMap(planDay -> planDay
                        .schedules()
                        .stream())
                .map(GetAiPlanResponse.PlanScheduleDetail::restaurantDetail)
                .filter(Objects::nonNull)
                .filter(restaurant -> restaurant.carbohydrate() != null
                        || restaurant.sodium() != null
                        || restaurant.fat() != null)
                .map(restaurant -> new NutritionEvaluationCollector.FoodNutritionEvaluation(
                        restaurant.menuName(),
                        new NutritionEvaluationResult(
                                List.of(),
                                NutritionEvaluationStatus.AVAILABLE,
                                List.of(),
                                restaurant.carbohydrate(),
                                restaurant.sodium(),
                                restaurant.fat()
                        )
                ))
                .toList();
    }

    // evaluateFoodNutrition Tool 호출 기록을 메뉴명 기준으로 재구성
    private Map<String, List<NutritionEvaluationResult>> nutritionEvaluationsByFoodName(
            List<NutritionEvaluationCollector.FoodNutritionEvaluation> nutritionEvaluations
    ) {

        return nutritionEvaluations
                .stream()
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

        return travelMinutesResolver.fill(
                response,
                createTravelRequest.transportation(),
                anchor
        );
    }

    /*
    기본 CRUD 모음
     */
    public void savePlan(Plan plan) {

        planRepository.save(plan);
    }
}
