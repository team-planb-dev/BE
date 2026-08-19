package com.planb.domain.travel.facade;


import com.planb.domain.travel.dto.request.CreatePlanRequest;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.MakeRecommendFoodsRequest;

import com.planb.domain.travel.dto.request.SearchPlannedPlaceRequest;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.dto.response.SearchPlannedPlaceResponse;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.service.PlanDayService;
import com.planb.domain.travel.service.PlanService;
import com.planb.domain.travel.service.PlannedPlaceService;
import com.planb.domain.travel.service.TravelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TravelFacade {

    private final TravelService travelService;
    private final PlannedPlaceService plannedPlaceService;
    private final PlanService planService;
    private final PlanDayService planDayService;


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
    public void makeTravelOptionsAndRecommend(CreateTravelRequest createTravelRequest){

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
                                .travelName()));

        // 해당 객체 기반으로 AI 호출하여 일정 생성하기

        // 생성한 AI 일정과 Plan을 연결


    }



}
