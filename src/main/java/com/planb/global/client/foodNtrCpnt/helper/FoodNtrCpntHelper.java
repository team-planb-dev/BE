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
                .toList();

        List<FoodNtrCpntResponse.Item> exactMatches = candidates
                .stream()
                .filter(item ->
                        sameFoodName(
                                item.foodName(),
                                foodName
                        )
                )
                .limit(3)
                .toList();

        if (!exactMatches.isEmpty()) {
            return exactMatches;
        }

        List<FoodNtrCpntResponse.Item> referenceMatches = candidates
                .stream()
                .filter(item ->
                        sameFoodName(
                                item.foodReferenceName(),
                                foodName
                        )
                )
                .limit(2)
                .toList();

        if (referenceMatches.size() == 1) {
            return referenceMatches;
        }

        return List.of();
    }

    // 식품명 비교
    private boolean sameFoodName(
            String candidateFoodName,
            String requestedFoodName
    ) {

        if (candidateFoodName == null || requestedFoodName == null) {
            return false;
        }

        return candidateFoodName
                .trim()
                .equalsIgnoreCase(
                        requestedFoodName.trim()
                );
    }
}
