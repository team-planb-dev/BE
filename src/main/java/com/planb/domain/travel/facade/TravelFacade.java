package com.planb.domain.travel.facade;


import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.service.FoodInfoService;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.health.service.MedicationInfoService;
import com.planb.domain.travel.dto.request.*;

import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.dto.response.SearchPlannedPlaceResponse;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class TravelFacade {

    /*
    Health Domain Service
     */
    private final HealthService healthService;
    private final FoodInfoService foodInfoService;
    private final MedicationInfoService medicationInfoService;


    /*
    Travel Domain Service
     */
    private final TravelService travelService;
    private final PlannedPlaceService plannedPlaceService;
    private final PlanService planService;
    private final PlanDayService planDayService;
    private final PlanScheduleService planScheduleService;


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
            Long userId
    ) {

        // Travel 객체 생성하기
        Travel travel = travelService
                .createTravel(createTravelRequest);

        // Travel 객체 생성 후 , 저장
        travelService.saveTravel(travel);

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

                    // PlanDay와 연결된 PlanSchedule 객체 리스트 생성 후 저장
                    planScheduleService.savePlanScheduleAll(
                            planScheduleService.makePlanScheduleList(
                                    planDay,
                                    planDayDetail.schedules()
                            )
                    );
                });

        // 생성된 AI 여행일정 응답 반환
        return createPlanAiResponse;
    }

}
