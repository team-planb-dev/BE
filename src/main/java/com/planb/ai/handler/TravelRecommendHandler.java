package com.planb.ai.handler;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.client.OpenAiClient;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.dto.response.RestaurantRecommendResult;
import com.planb.ai.mcp.TourismTool;
import com.planb.ai.prompt.AttractionRecommendPrompt;
import com.planb.ai.prompt.CafeRecommendPrompt;
import com.planb.ai.prompt.FoodRecommendPrompt;
import com.planb.ai.prompt.RestaurantRecommendPrompt;
import com.planb.ai.prompt.TravelPlanPrompt;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.entity.constant.DateType;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

import java.util.function.Predicate;

@Component
@RequiredArgsConstructor
public class TravelRecommendHandler {

    /*
    Client
     */
    private final OpenAiClient openAiClient;

    /*
    Helper
     */
    private final ObjectMapper objectMapper;
    private final BeanOutputConverter<CreatePlanAiResponse> createPlanAiResponseConverter;

    /*
    Tool
     */
    private final TourismTool tourismTool;

    // 지역에 따른 음식 추천 받기
    public MakeRecommendFoodResponse makeRecommendFood
    (MakeFoodRecommendCallRequest request){

        return openAiClient
                .call(
                        new FoodRecommendPrompt(
                                request
                                        .request()
                                        .fullLocation()),
                        MakeRecommendFoodResponse.class);
    }



    // AI로 사용자의 동행자 및 건강정보를 반영하여 일정생성
    // planDays가 비어있거나 dateType 기준 예상 일수와 다르면 STEP 9 조립 실패로 간주하고 재시도 대상에 포함
    public CreatePlanAiResponse createPlanByAi(TravelPlanContext travelPlanContext){

        return openAiClient
                .call(
                        new TravelPlanPrompt(
                                travelPlanContext,
                                objectMapper
                        ),
                        createPlanAiResponseConverter,
                        hasPlanDays(
                                travelPlanContext
                                        .createTravelRequest()
                                        .dateType()
                        ),
                        tourismTool
                );
    }

    // planDays가 null이거나 비어있으면 무효, dateType 기준 예상 일수와 다르면 무효
    // (조립을 완료하지 못했거나 일부 날짜를 누락한 응답)
    private static Predicate<CreatePlanAiResponse> hasPlanDays(DateType dateType) {

        int expectedDayCount = dateType.getPlusDays() + 1;

        return response -> response != null
                && response.planDays() != null
                && response.planDays().size() == expectedDayCount;
    }


    // CAFE_REST 슬롯 재추천 (여행 전체 기간 카페 중복 보정용)
    // excludeNames는 PlanService가 추적한 값 그대로 사용
    public PlaceWithRouteResult recommendCafe(CafeRecommendPrompt cafeRecommendPrompt){

        return openAiClient
                .call(
                        cafeRecommendPrompt,
                        PlaceWithRouteResult.class,
                        tourismTool
                );
    }

    // RESTAURANT 슬롯 재추천 (여행 전체 기간 메뉴 중복 보정용)
    // excludeMenuNames는 PlanService가 추적한 값 그대로 사용
    public RestaurantRecommendResult recommendRestaurant(RestaurantRecommendPrompt restaurantRecommendPrompt){

        return openAiClient
                .call(
                        restaurantRecommendPrompt,
                        RestaurantRecommendResult.class,
                        tourismTool
                );
    }

    // ATTRACTION 슬롯 재추천 (여행 전체 기간 관광지 중복 보정용)
    // excludeNames는 PlanService가 추적한 값 그대로 사용
    public PlaceWithRouteResult recommendAttraction(AttractionRecommendPrompt attractionRecommendPrompt){

        return openAiClient
                .call(
                        attractionRecommendPrompt,
                        PlaceWithRouteResult.class,
                        tourismTool
                );
    }

}
