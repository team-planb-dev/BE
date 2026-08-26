package com.planb.global.client.foodNtrCpnt.helper;

import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FoodNtrCpntHelper {

    // 식품 영양정보 검색 결과 정제
    public List<FoodNtrCpntResponse.Item> filterFoodNutrition
    (FoodNtrCpntResponse response,
     String foodName) {

        if (response == null
                || response.body() == null
                || response.body().items() == null) {

            return List.of();
        }

        List<FoodNtrCpntResponse.Item> candidates = response
                .body()
                .items()
                .stream()
                .filter(item ->
                        "음식".equals(
                                item.dbGroupName()
                        )
                )
                .filter(item ->
                        item.foodOriginName() != null
                                && !item.foodOriginName()
                                .contains("급식")
                )
                .toList();

        List<FoodNtrCpntResponse.Item> outsideFoodCandidates = candidates
                .stream()
                .filter(item ->
                        item.foodOriginName()
                                .startsWith("외식")
                )
                .toList();

        if (!outsideFoodCandidates.isEmpty()) {
            candidates = outsideFoodCandidates;
        }

        String normalizedFoodName =
                normalizeFoodName(foodName);

        List<FoodNtrCpntResponse.Item> exactMatches = candidates
                .stream()
                .filter(item ->
                        normalizeFoodName(
                                item.foodName()
                        ).equals(normalizedFoodName)
                )
                .limit(3)
                .toList();

        if (!exactMatches.isEmpty()) {
            return exactMatches;
        }

        return candidates
                .stream()
                .filter(item ->
                        normalizeFoodName(
                                item.foodName()
                        ).contains(normalizedFoodName)
                )
                .limit(3)
                .toList();
    }

    // 식품명 비교를 위한 이름 정규화
    private String normalizeFoodName(String foodName) {

        if (foodName == null) {
            return "";
        }

        int separatorIndex = foodName
                .lastIndexOf("_");

        if (separatorIndex >= 0) {
            return foodName
                    .substring(separatorIndex + 1)
                    .trim();
        }

        return foodName
                .trim();
    }
}