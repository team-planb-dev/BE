package com.planb.ai.handler;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.client.OpenAiClient;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.mcp.TourismTool;
import com.planb.ai.prompt.CafeRecommendPrompt;
import com.planb.ai.prompt.FoodRecommendPrompt;
import com.planb.ai.prompt.TravelPlanPrompt;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.stereotype.Component;

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
    public CreatePlanAiResponse createPlanByAi(TravelPlanContext travelPlanContext){

        return openAiClient
                .call(
                        new TravelPlanPrompt(
                                travelPlanContext,
                                objectMapper
                        ),
                        createPlanAiResponseConverter,
                        tourismTool
                );
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


}