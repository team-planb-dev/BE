package com.planb.domain.travel.facade;


import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.domain.health.service.FoodInfoService;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.health.service.MedicationInfoService;
import com.planb.domain.travel.dto.request.*;

import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.dto.response.SearchPlannedPlaceResponse;
import com.planb.domain.travel.entity.*;
import com.planb.domain.travel.service.*;
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
    public CreatePlanAiResponse makeTravelOptionsAndRecommend(
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

        // UserId 기반 Health 객체 리스트를 활용해 AI 컨텍스트 생성하기
        List<TravelHealthContext> healthContexts =
                healthService.getHealthListByUserId(userId)
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

        // Travel 정보와 Health 컨텍스트 기반으로 AI 일정 생성하기
        CreatePlanAiResponse createPlanAiResponse =
                planService.makePlanByAi(
                        new TravelPlanContext(
                                createTravelRequest,
                                healthContexts
                        )
                );

        // AI 응답의 PlanSchedule 태그를 모두 모아 Plan에 반영
        plan.updateTags(
                planService.aggregateTags(
                        createPlanAiResponse.planDays()
                )
        );
        planService.savePlan(plan);

        // AI 응답 기반으로 PlanDay, PlanSchedule 객체 생성 후 저장
        createPlanAiResponse.planDays()
                .forEach(planDayDetail -> {

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

        // 생성된 AI 여행일정 응답 반환
        return createPlanAiResponse;
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

}