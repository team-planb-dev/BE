package com.planb.ai.handler;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.client.OpenAiClient;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.mcp.TourismTool;
import com.planb.ai.prompt.FoodRecommendPrompt;
import com.planb.ai.prompt.TravelPlanPrompt;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import lombok.RequiredArgsConstructor;
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
                        CreatePlanAiResponse.class,
                        tourismTool
        );
    }




}
