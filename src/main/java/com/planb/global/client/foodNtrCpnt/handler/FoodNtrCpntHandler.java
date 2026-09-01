package com.planb.global.client.foodNtrCpnt.handler;

import com.planb.global.client.foodNtrCpnt.FoodNtrCpntClient;
import com.planb.global.client.foodNtrCpnt.dto.request.FoodNtrCpntSearchRequest;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.client.foodNtrCpnt.helper.FoodNtrCpntHelper;
import com.planb.global.client.helper.DataUriBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.List;

@RequiredArgsConstructor
@Component
public class FoodNtrCpntHandler {

    private final FoodNtrCpntClient foodNtrCpntClient;

    /*
    Helper
     */
    private final FoodNtrCpntHelper foodNtrCpntHelper;

    // 음식 이름으로 영양성분 조회
    public Mono<FoodNtrCpntResponse> searchFoodNutrition
    (FoodNtrCpntSearchRequest request) {

        URI uri = DataUriBuilder
                .from(
                        foodNtrCpntClient.baseUrl(),
                        "/getFoodNtrCpntDbInq02",
                        foodNtrCpntClient.serviceKey()
                )
                .queryParam(
                        "pageNo",
                        request.pageNo()
                )
                .queryParam(
                        "numOfRows",
                        request.numOfRows()
                )
                .queryParam(
                        "type",
                        "json"
                )
                .queryParam(
                        "FOOD_NM_KR",
                        request.foodName()
                )
                .queryParam(
                        "DB_CLASS_NM",
                        request.dbClassName()
                )
                .build();

        return foodNtrCpntClient
                .get(
                        uri,
                        FoodNtrCpntResponse.class
                );
    }


    /*
    Parser 메소드
     */

    // 식품 영양정보 조회 후 검색 결과 정제
    public Mono<List<FoodNtrCpntResponse.Item>> getFoodNutrition

            (FoodNtrCpntSearchRequest request) {

        return searchFoodNutrition(request)

                .map(response ->
                        foodNtrCpntHelper
                                .filterFoodNutrition(
                                        response,
                                        request.foodName()
                                )
                );

    }


}
