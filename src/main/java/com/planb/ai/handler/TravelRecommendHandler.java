package com.planb.ai.handler;


import com.planb.ai.client.OpenAiClient;
import com.planb.ai.dto.request.MakeFoodRecommendCallRequest;
import com.planb.ai.prompt.FoodRecommendPrompt;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TravelRecommendHandler {

    private final OpenAiClient openAiClient;

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


    // TODO : Travel 객체 정보에 따른 일정 생성하기


}
