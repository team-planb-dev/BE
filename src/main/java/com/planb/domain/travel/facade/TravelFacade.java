package com.planb.domain.travel.facade;


import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.domain.health.service.FoodInfoService;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.health.service.MedicationInfoService;
import com.planb.domain.travel.dto.request.*;

import com.planb.domain.travel.dto.response.CreatePlanResponse;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.dto.response.SearchPlannedPlaceResponse;
import com.planb.domain.travel.entity.*;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.service.*;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.config.exception.domain.ForbiddenException;
import com.planb.query.health.service.HealthQueryService;
import com.planb.query.health.service.MedicationInfoQueryService;
import com.planb.query.travel.dto.response.PlanDayQueryResponse;
import com.planb.query.travel.dto.response.PlanQueryResponse;
import com.planb.query.travel.dto.response.RestaurantDetailQueryResponse;
import com.planb.query.travel.dto.response.TravelConditionQueryResponse;
import com.planb.query.travel.service.*;
import com.planb.query.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class TravelFacade {

    /*
    User Domain Service
     */
    private final UserQueryService userQueryService;

    /*
    Health Domain Service
     */
    private final HealthService healthService;
    private final FoodInfoService foodInfoService;
    private final MedicationInfoService medicationInfoService;

    /*
    Health Query Service
     */
    private final HealthQueryService healthQueryService;
    private final MedicationInfoQueryService medicationInfoQueryService;


    /*
    Travel Domain Service
     */
    private final TravelService travelService;
    private final PlannedPlaceService plannedPlaceService;
    private final PlanService planService;
    private final PlanDayService planDayService;
    private final PlanScheduleService planScheduleService;
    private final RestaurantDetailService restaurantDetailService;
    private final PlanEditCacheService planEditCacheService;

    /*
    Travel Query Service
     */
    private final TravelQueryService travelQueryService;
    private final PlanQueryService planQueryService;
    private final PlanScheduleQueryService planScheduleQueryService;
    private final PlanDayQueryService planDayQueryService;
    private final RestaurantDetailQueryService restaurantDetailQueryService;


    /**
     * AI로 해당 지역 추천음식 키워드 받기
     */
    public MakeRecommendFoodResponse showRecommendFoods
    (MakeRecommendFoodsRequest request){
        return travelService.makeRecommendFoodResponse(request);
    }

    /**
     * Kor2Service API로 키워드에 따른 숙박,관광지 검색하기
     */
    public Mono<SearchPlannedPlaceResponse> searchPlannedPlaceByText
    (SearchPlannedPlaceRequest searchPlannedPlaceRequest){

        return plannedPlaceService.searchPlannedPlace(searchPlannedPlaceRequest);
    }


    /**
     * 사용자 입력 받은 후, 해당 데이터 기반으로 여행일정 생성하기
     */
    @Transactional
    public CreatePlanResponse makeTravelOptionsAndRecommend(
            CreateTravelRequest createTravelRequest,
            String username
    ) {

        // Redis 캐시에서 userId 조회
        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        // Travel 객체 생성하기
        Travel travel = travelService
                .createTravel(createTravelRequest, userId);

        // Travel 객체 생성 후 , 저장
        travelService.saveTravel(travel);

        // PlannedPlan 객체 생성 후 , 저장하기
        plannedPlaceService
                .savePlannedPlaceList(plannedPlaceService
                        .makePlannedPlace(CreatePlannedPlaceRequest
                                .from(
                                        travel,
                                        createTravelRequest)));

        // Travel과 연결된 Plan객체 생성
        Plan plan = planService.createPlan(
                new CreatePlanRequest(
                        travel,
                        createTravelRequest
                                .travelName()
                )
        );

        // Plan 객체 저장
        planService.savePlan(plan);

        // UserId 기반 Health 컨텍스트 생성하기
        List<TravelHealthContext> healthContexts =
                buildHealthContexts(userId);

        // Travel 정보와 Health 컨텍스트 기반으로 AI 일정 생성하기
        CreatePlanAiResponse createPlanAiResponse =
                planService.makePlanByAi(
                        new TravelPlanContext(
                                createTravelRequest,
                                healthContexts
                        )
                );

        // AI 응답의 PlanSchedule 태그를 모두 모아 Plan에 반영
        Set<RecommendationTag> aggregatedTags =
                planService.aggregateTags(
                        createPlanAiResponse.planDays()
                );
        plan.updateTags(aggregatedTags);
        planService.savePlan(plan);

        // AI 응답 기반으로 PlanDay, PlanSchedule, RestaurantDetail 생성 후 저장
        materializePlanDays(plan, createPlanAiResponse.planDays());

        // 생성된 AI 여행일정 응답 반환
        return CreatePlanResponse.of(
                aggregatedTags,
                createPlanAiResponse
        );
    }

    /**
     * Travel ID를 기반으로 생성된 AI 여행일정 전체 조회하기
     */
    @Transactional(readOnly = true)
    public GetAiPlanResponse getAiPlan(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        Long travelId =
                getAiPlanRequest.travelId();

        // travelId가 이 userId 소유인지 검증
        if (!travelQueryService.existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }

        TravelConditionQueryResponse travelCondition =
                travelQueryService
                        .getTravelConditionQueryResponse(
                                travelId
                        );

        List<HealthSummaryQueryResponse> healthSummaries =
                healthQueryService
                        .getHealthSummaryList(
                                userId
                        );

        List<LocalTime> medicationTimes =
                medicationInfoQueryService
                        .getMedicationTimes(
                                userId
                        );

        PlanQueryResponse plan =
                planQueryService
                        .getPlanByTravelId(
                                travelId
                        );

        List<PlanDayQueryResponse> planDays =
                planDayQueryService
                        .getPlanDaysByPlanId(
                                plan.planId()
                        );

        List<PlanSchedule> planSchedules =
                planScheduleQueryService
                        .getPlanSchedulesByPlanDayIds(
                                planDays.stream()
                                        .map(
                                                PlanDayQueryResponse::planDayId
                                        )
                                        .toList()
                        );

        List<RestaurantDetailQueryResponse> restaurantDetails =
                restaurantDetailQueryService
                        .getRestaurantDetailsByPlanScheduleIds(
                                planSchedules.stream()
                                        .map(
                                                PlanSchedule::getId
                                        )
                                        .toList()
                        );

        return GetAiPlanResponse.from(
                plan,
                travelCondition,
                healthSummaries,
                medicationTimes,
                planDays,
                planSchedules,
                restaurantDetails
        );
    }

    /**
     * 기존 일정을 자연어 수정 요청에 맞춰 AI로 미리보기 생성 (DB에는 반영하지 않고 Redis에만 저장)
     */
    @Transactional(readOnly = true)
    public EditPlanPreviewResponse makeEditPlanPreview(
            EditPlanRequest editPlanRequest,
            String username
    ) {

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        Long travelId = editPlanRequest.travelId();

        // travelId가 이 userId 소유인지 검증
        if (!travelQueryService.existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }

        // 현재 확정되어 있는 일정 조회 (getAiPlan 재사용)
        GetAiPlanResponse currentPlan =
                getAiPlan(
                        new GetAiPlanRequest(travelId),
                        username
                );

        // Travel 엔티티 기반으로 원본 CreateTravelRequest 재구성
        Travel travel = travelService.findTravelById(travelId);

        List<CreateTravelRequest.PlannedPlaceDetail> plannedPlaceDetails =
                plannedPlaceService.findAllByTravel(travel)
                        .stream()
                        .map(plannedPlace ->
                                new CreateTravelRequest.PlannedPlaceDetail(
                                        plannedPlace.getLocationName(),
                                        plannedPlace.getLocation()
                                )
                        )
                        .toList();

        CreateTravelRequest createTravelRequest =
                CreateTravelRequest.from(
                        travel,
                        plannedPlaceDetails
                );

        // UserId 기반 Health 컨텍스트 생성하기
        List<TravelHealthContext> healthContexts =
                buildHealthContexts(userId);

        // AI로 수정안 생성
        EditPlanAiResponse editPlanAiResponse =
                planService.makeEditPlanByAi(
                        new PlanEditContext(
                                createTravelRequest,
                                healthContexts,
                                currentPlan,
                                editPlanRequest.editRequest()
                        )
                );

        // Redis에 수정안 저장 (5단계 저장 확정에서 사용)
        planEditCacheService.saveEditResult(
                travelId,
                editPlanAiResponse
        );

        // Before/After 반환
        return new EditPlanPreviewResponse(
                currentPlan,
                editPlanAiResponse
        );
    }

    /**
     * Redis에 저장된 수정안을 실제 일정에 반영하기 (기존 PlanDay/PlanSchedule/RestaurantDetail을 지우고 재생성)
     */
    @Transactional
    public CreatePlanResponse confirmEditPlan(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        Long travelId = getAiPlanRequest.travelId();

        // travelId가 이 userId 소유인지 검증
        if (!travelQueryService.existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }

        // Redis에서 수정안 조회 (없거나 만료면 예외)
        EditPlanAiResponse editPlanAiResponse =
                planEditCacheService.findEditResult(travelId)
                        .orElseThrow(() ->
                                new BaseException(
                                        PlanEditExceptionEnum.EDIT_RESULT_NOT_FOUND
                                )
                        );

        PlanQueryResponse planQueryResponse =
                planQueryService.getPlanByTravelId(travelId);

        Plan plan = planService.findPlanById(planQueryResponse.planId());

        // 기존 PlanDay/PlanSchedule/RestaurantDetail 삭제 (자식 -> 부모 순서)
        List<PlanDay> existingPlanDays =
                planDayService.findAllByPlan(plan);

        List<PlanSchedule> existingPlanSchedules =
                planScheduleService.findAllByPlanDayIn(existingPlanDays);

        restaurantDetailService.deleteAllByPlanScheduleIn(existingPlanSchedules);
        planScheduleService.deleteAllByPlanDayIn(existingPlanDays);
        planDayService.deleteAllByPlan(plan);

        // 수정안의 PlanSchedule 태그를 모두 모아 Plan에 반영
        Set<RecommendationTag> aggregatedTags =
                planService.aggregateTags(
                        editPlanAiResponse.planDays()
                );
        plan.updateTags(aggregatedTags);
        planService.savePlan(plan);

        // 수정안 기반으로 PlanDay, PlanSchedule, RestaurantDetail 재생성 후 저장
        materializePlanDays(plan, editPlanAiResponse.planDays());

        // Redis 캐시 정리
        planEditCacheService.deleteEditResult(travelId);

        // CreatePlanResponse 재사용을 위한 CreatePlanAiResponse 임시 래핑
        CreatePlanAiResponse wrapped =
                new CreatePlanAiResponse(editPlanAiResponse.planDays());

        return CreatePlanResponse.of(
                aggregatedTags,
                wrapped
        );
    }

    /**
     * Redis에 저장된 수정안을 버리고 원래 일정 그대로 유지하기 (DB는 건드리지 않음)
     */
    @Transactional
    public void cancelEditPlan(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        Long travelId = getAiPlanRequest.travelId();

        // travelId가 이 userId 소유인지 검증
        if (!travelQueryService.existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }

        planEditCacheService.deleteEditResult(travelId);
    }

    // UserId 기반 Health 객체 리스트를 활용해 AI 컨텍스트 생성하기 (Create/Edit 공통 사용)
    private List<TravelHealthContext> buildHealthContexts(Long userId) {

        return healthService.getHealthListByUserId(userId)
                .stream()
                .map(health ->
                        TravelHealthContext.from(
                                health,
                                foodInfoService.getFoodInfoList(
                                        health.getId()
                                ),
                                medicationInfoService.findAllByHealthId(
                                        health.getId()
                                )
                        )
                )
                .toList();
    }

    // AI 응답의 planDays 기반으로 PlanDay/PlanSchedule/RestaurantDetail 생성 후 저장 (Create/Edit 공통 사용)
    private void materializePlanDays(
            Plan plan,
            List<CreatePlanAiResponse.PlanDayDetail> planDays
    ) {

        planDays.forEach(planDayDetail -> {

            // AI 응답 기반으로 PlanDay 객체 생성
            PlanDay planDay =
                    planDayService.createPlanDay(
                            new CreatePlanDayRequest(
                                    plan,
                                    planDayDetail.dayNumber(),
                                    planDayDetail.date()
                            )
                    );

            // PlanDay 객체 저장
            planDayService.savePlanDay(planDay);

            // PlanDay와 연결된 PlanSchedule 객체 리스트 생성
            List<PlanSchedule> planSchedules =
                    planScheduleService.makePlanScheduleList(
                            planDay,
                            planDayDetail.schedules()
                    );

            // PlanSchedule 객체 리스트 일괄 저장
            planScheduleService.savePlanScheduleAll(
                    planSchedules
            );

            // RestaurantDetail 객체 리스트 생성
            List<RestaurantDetail> restaurantDetails =
                    restaurantDetailService.makeRestaurantDetailList(
                            planSchedules,
                            planDayDetail.schedules()
                    );

            // RestaurantDetail 객체 리스트 일괄 저장
            restaurantDetailService.saveRestaurantDetailAll(
                    restaurantDetails
            );
        });
    }

}
