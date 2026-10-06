package com.planb.domain.travel.service;

import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.domain.health.service.FoodInfoService;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.health.service.MedicationInfoService;
import com.planb.domain.travel.dto.request.CreatePlanDayRequest;
import com.planb.domain.travel.dto.request.CreatePlanRequest;
import com.planb.domain.travel.dto.request.CreatePlannedPlaceRequest;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.MakeRecommendFoodsRequest;
import com.planb.domain.travel.dto.response.CreatePlanResponse;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.dto.response.SaveTravelResponse;
import com.planb.domain.travel.dto.response.ShareTravelResponse;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.health.entity.Health;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.TravelHealth;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.entity.RestaurantDetail;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.global.config.exception.TravelExceptionEnum;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.query.health.service.HealthQueryService;
import com.planb.query.health.service.MedicationInfoQueryService;
import com.planb.query.travel.service.PlanQueryService;
import com.planb.query.user.service.UserQueryService;
import com.planb.domain.travel.repository.TravelHealthRepository;
import com.planb.domain.travel.repository.TravelRepository;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.repository.UserRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class TravelService {

    /*
     repository
     */
    private final TravelRepository travelRepository;
    private final TravelHealthRepository travelHealthRepository;
    private final UserRepository userRepository;

    /*
     handler
     */
    private final TravelRecommendHandler travelRecommendHandler;
    private final TravelTransactionService travelTransactionService;
    private final UserQueryService userQueryService;
    private final HealthQueryService healthQueryService;
    private final HealthService healthService;
    private final FoodInfoService foodInfoService;
    private final MedicationInfoService medicationInfoService;
    private final PlannedPlaceService plannedPlaceService;
    private final PlanService planService;
    private final PlanDayService planDayService;
    private final PlanScheduleService planScheduleService;
    private final RestaurantDetailService restaurantDetailService;
    private final MedicationInfoQueryService medicationInfoQueryService;
    private final PlanQueryService planQueryService;
    private final PlanEditCacheService planEditCacheService;
    private final MeterRegistry meterRegistry;


    public List<TravelHealthContext> loadHealthContexts(
            CreateTravelRequest request,
            Long userId
    ) {

        return recordStage(
                "health_snapshot",
                () -> travelTransactionService.readOnly(() -> {
                    userQueryService.findById(userId);

                    List<Health> selectedHealths = findSelectedHealths(
                            request.healthIds(),
                            userId
                    );

                    return buildHealthContexts(selectedHealths);
                })
        );
    }

    public CreatePlanResponse saveGeneratedPlan(
            CreateTravelRequest request,
            Long userId,
            CreatePlanAiResponse aiResponse
    ) {

        Set<RecommendationTag> tags = planService.aggregateTags(aiResponse.planDays());

        return recordStage(
                "persistence",
                () -> travelTransactionService.write(() -> {
                    userQueryService.findById(userId);

                    List<Health> selectedHealths = findSelectedHealths(
                            request.healthIds(),
                            userId
                    );

                    Travel travel = createTravel(request, userId);
                    saveTravel(travel);
                    saveTravelHealths(travel, selectedHealths);

                    plannedPlaceService.savePlannedPlaceList(
                            plannedPlaceService.makePlannedPlace(
                                    CreatePlannedPlaceRequest.from(travel, request)
                            )
                    );

                    Plan plan = planService.createPlan(
                            new CreatePlanRequest(travel, request.travelName())
                    );
                    planService.savePlan(plan);
                    plan.updateTags(tags);
                    planService.savePlan(plan);

                    materializePlanDays(plan, aiResponse.planDays());

                    return CreatePlanResponse.of(
                            travel,
                            tags,
                            aiResponse
                    );
                })
        );
    }

    private <T> T recordStage(
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
            sample.stop(
                    Timer
                            .builder("planb.travel.plan.stage")
                            .tag("flow", "create")
                            .tag("stage", stage)
                            .tag("outcome", outcome)
                            .register(meterRegistry)
            );
        }
    }

    public GetAiPlanResponse loadCurrentPlan(Long travelId) {

        List<Long> healthIds = findHealthIdsByTravelId(travelId);

        return planQueryService.getPlanDetailResponse(
                travelId,
                healthQueryService.getHealthSummaryListByHealthIds(healthIds),
                medicationInfoQueryService.getMedicationTimesByHealthIds(healthIds)
        );
    }

    public PlanEditContext makeEditContext(
            Long travelId,
            GetAiPlanResponse currentPlan,
            String editRequest
    ) {

        Travel travel = findTravelById(travelId);

        List<CreateTravelRequest.PlannedPlaceDetail> plannedPlaces = plannedPlaceService
                .findAllByTravel(travel)
                .stream()
                .map(place -> new CreateTravelRequest.PlannedPlaceDetail(
                        place.getLocationName(),
                        place.getLocation()
                ))
                .toList();

        List<Health> healths = findHealthListByTravelId(travelId);

        List<Long> healthIds = healths
                .stream()
                .map(Health::getId)
                .toList();

        CreateTravelRequest request = CreateTravelRequest.from(
                travel,
                plannedPlaces,
                healthIds
        );

        return new PlanEditContext(
                request,
                buildHealthContexts(healths),
                currentPlan,
                editRequest
        );
    }

    public PlanEditContext prepareEditContext(
            Long travelId,
            String editRequest
    ) {

        return travelTransactionService.readOnly(() -> {
            GetAiPlanResponse currentPlan = loadCurrentPlan(travelId);

            return makeEditContext(
                    travelId,
                    currentPlan,
                    editRequest
            );
        });
    }

    public CreatePlanResponse confirmEditPlan(Long travelId) {

        Optional<EditPlanAiResponse> consumed =
                planEditCacheService.consumeEditResult(travelId);

        if (consumed.isEmpty()) {
            EditPlanAiResponse confirmed = planEditCacheService
                    .findConfirmedResult(travelId)
                    .orElseThrow(() -> new BaseException(
                            PlanEditExceptionEnum.EDIT_RESULT_NOT_FOUND
                    ));

            return confirmedResponse(travelId, confirmed);
        }

        EditPlanAiResponse editResponse = consumed.get();
        Plan plan = planService.findPlanById(
                planQueryService
                        .getPlanByTravelId(travelId)
                        .planId()
        );

        List<PlanDay> existingDays = planDayService.findAllByPlan(plan);
        List<PlanSchedule> existingSchedules =
                planScheduleService.findAllByPlanDayIn(existingDays);

        restaurantDetailService.deleteAllByPlanScheduleIn(existingSchedules);
        planScheduleService.deleteAllByPlanDayIn(existingDays);
        planDayService.deleteAllByPlan(plan);

        Set<RecommendationTag> tags = planService.aggregateTags(editResponse.planDays());
        plan.updateTags(tags);
        planService.savePlan(plan);

        materializePlanDays(plan, editResponse.planDays());

        planEditCacheService.markConfirmedAfterCommit(travelId, editResponse);

        return confirmedResponse(travelId, editResponse);
    }

    public CreatePlanResponse confirmEditPlanInTransaction(Long travelId) {

        return travelTransactionService.write(() -> confirmEditPlan(travelId));
    }

    private CreatePlanResponse confirmedResponse(
            Long travelId,
            EditPlanAiResponse editResponse
    ) {

        return CreatePlanResponse.of(
                findTravelById(travelId),
                planService.aggregateTags(editResponse.planDays()),
                new CreatePlanAiResponse(editResponse.planDays())
        );
    }

    private List<Health> findSelectedHealths(
            List<Long> healthIds,
            Long userId
    ) {

        if (healthIds == null || healthIds.isEmpty()) {
            throw new BaseException(TravelExceptionEnum.COMPANION_REQUIRED);
        }

        return healthIds
                .stream()
                .distinct()
                .map(healthId -> {
                    if (!healthQueryService.checkHealthWithUser(healthId, userId)) {
                        throw new BaseException(TravelExceptionEnum.COMPANION_NOT_OWNED);
                    }

                    return healthService.getHealthById(healthId);
                })
                .toList();
    }

    private List<TravelHealthContext> buildHealthContexts(List<Health> healths) {

        return healths
                .stream()
                .filter(Health::isSensitiveAgree)
                .map(health -> TravelHealthContext.from(
                        health,
                        foodInfoService.getFoodInfoList(health.getId()),
                        medicationInfoService.findAllByHealthId(health.getId())
                ))
                .toList();
    }

    private void materializePlanDays(
            Plan plan,
            List<CreatePlanAiResponse.PlanDayDetail> planDays
    ) {

        planDays.forEach(detail -> {
            PlanDay day = planDayService.createPlanDay(
                    new CreatePlanDayRequest(
                            plan,
                            detail.dayNumber(),
                            detail.date()
                    )
            );
            planDayService.savePlanDay(day);

            List<PlanSchedule> schedules = planScheduleService.makePlanScheduleList(
                    day,
                    detail.schedules()
            );
            planScheduleService.savePlanScheduleAll(schedules);

            List<RestaurantDetail> restaurants = restaurantDetailService.makeRestaurantDetailList(
                    schedules,
                    detail.schedules()
            );
            restaurantDetailService.saveRestaurantDetailAll(restaurants);
        });
    }


    // Travel 객체 생성
    public Travel createTravel(CreateTravelRequest createTravelRequest, Long userId) {

        User user = userRepository.getReferenceById(userId);

        return Travel
                .builder()
                .user(user)
                .travelName(createTravelRequest
                        .travelName())
                .locationDo(createTravelRequest
                        .locationDo())
                .locationSigungu(createTravelRequest
                        .locationSigungu())
                .startDate(createTravelRequest
                        .startDate())
                .dateType(createTravelRequest
                        .dateType())
                .endDate(calculateEndDate(
                        createTravelRequest
                                .startDate(),
                        createTravelRequest
                                .dateType()))
                .transportation(createTravelRequest
                        .transportation())
                .decidedLocation(createTravelRequest
                        .decidedLocation())
                .travelStyle(createTravelRequest
                        .travelStyle())
                .travelTheme(createTravelRequest
                        .travelTheme())
                .localFoods(createTravelRequest
                        .localFoods())
                .recommendFoods(createTravelRequest
                        .recommendFoods())
                .build();
    }


    //  OpenAI API 호출 후, 해당 지역 음식 추천
    public MakeRecommendFoodResponse makeRecommendFoodResponse
    (MakeRecommendFoodsRequest makeRecommendFoodsRequest) {
        return travelRecommendHandler
                .makeRecommendFood(new MakeFoodRecommendCallRequest(makeRecommendFoodsRequest));
    }



    /*
    기본 CRUD 모음
     */

    // Travel 객체 단건 조회하기 (존재 검증은 호출부에서 이미 끝난 상태를 전제)
    public Travel findTravelById(Long travelId) {
        return travelRepository.getReferenceById(travelId);
    }

    // Travel 객체 저장하기
    public void saveTravel(Travel travel) {
        travelRepository.save(travel);
    }

    // Travel 객체 삭제하기
    public void deleteTravel(Long travelId) {
        travelRepository.deleteById(travelId);
    }

    /**
     * 중복 없이 저장하는 여행별 참여 구성원 연결
     */
    public void saveTravelHealths(
            Travel travel,
            List<Health> healths
    ) {

        travelHealthRepository.saveAll(healths
                .stream()
                .distinct()
                .map(health -> TravelHealth
                        .builder()
                        .travel(travel)
                        .health(health)
                        .build())
                .toList());
    }

    /**
     * 여행 생성 시 선택한 구성원 조회
     */
    public List<Health> findHealthListByTravelId(Long travelId) {

        return travelHealthRepository
                .findAllByTravelId(travelId)
                .stream()
                .map(TravelHealth::getHealth)
                .toList();
    }

    public List<Long> findHealthIdsByTravelId(Long travelId) {

        return findHealthListByTravelId(travelId)
                .stream()
                .map(Health::getId)
                .toList();
    }

    public SaveTravelResponse markSaved(Long travelId) {

        Travel travel = findTravelById(travelId);
        travel.markSaved();

        return new SaveTravelResponse(travelId, travel.isSaved());
    }

    public ShareTravelResponse issueShareLink(Long travelId) {

        Travel travel = findTravelById(travelId);

        if (!travel.isSaved()) {
            throw new BaseException(TravelExceptionEnum.TRAVEL_NOT_SAVED);
        }

        travel.issueShareToken(UUID
                        .randomUUID()
                        .toString());

        return new ShareTravelResponse(travelId, travel.getShareToken());
    }



    /*
    내부 헬퍼 메서드 모음
     */

    // 여행 마지막일 계산
    private LocalDate calculateEndDate(LocalDate startDate,
                                       DateType dateType) {

        return startDate
                .plusDays(dateType
                        .getPlusDays());
    }

}
