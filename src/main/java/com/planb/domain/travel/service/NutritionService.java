package com.planb.domain.travel.service;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.dto.nutrition.NutritionInfo;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.helper.NutritionEvaluator;
import com.planb.global.client.foodNtrCpnt.dto.request.FoodNtrCpntSearchRequest;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.client.foodNtrCpnt.handler.FoodNtrCpntHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NutritionService {

    private final FoodNtrCpntHandler foodNtrCpntHandler;
    private final NutritionEvaluator nutritionEvaluator;

    // 음식 영양정보 조회 및 질환별 영양성분 평가
    public Mono<NutritionEvaluationResult> evaluateFoodNutrition(
            String foodName,
            DiseaseType diseaseType
    ) {

        return foodNtrCpntHandler
                .getFoodNutrition(
                        FoodNtrCpntSearchRequest.of(foodName)
                )
                .map(items -> {

                    if (items.isEmpty()) {
                        return new NutritionEvaluationResult(
                                diseaseType,
                                NutritionEvaluationStatus.UNAVAILABLE,
                                List.of(),
                                null,
                                null,
                                null
                        );
                    }

                    FoodNtrCpntResponse.Item item =
                            findFoodItem(
                                    items,
                                    foodName
                            );

                    NutritionInfo nutritionInfo =
                            toNutritionInfo(item);

                    return nutritionEvaluator.evaluate(
                            diseaseType,
                            nutritionInfo
                    );
                });
    }

    // 음식 이름 정확 일치 우선 조회, 없으면 첫 번째 결과 반환
    private FoodNtrCpntResponse.Item findFoodItem(
            List<FoodNtrCpntResponse.Item> items,
            String foodName
    ) {

        return items.stream()
                .filter(item ->
                        item.foodName() != null
                                && item.foodName()
                                .trim()
                                .equalsIgnoreCase(
                                        foodName.trim()
                                )
                )
                .findFirst()
                .orElse(items.getFirst());
    }

    // 식약처 영양정보를 평가용 NutritionInfo로 변환
    private NutritionInfo toNutritionInfo(
            FoodNtrCpntResponse.Item item
    ) {

        return new NutritionInfo(
                parseNutritionValue(item.carbohydrate()),
                parseNutritionValue(item.sugar()),
                parseNutritionValue(item.dietaryFiber()),
                parseNutritionValue(item.sodium()),
                parseNutritionValue(item.saturatedFat()),
                parseNutritionValue(item.transFat()),
                parseNutritionValue(item.cholesterol()),
                parseNutritionValue(item.fat())
        );
    }

    // 식약처 String 영양성분 값을 Double 타입으로 변환
    private Double parseNutritionValue(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return Double.parseDouble(
                value.trim()
        );
    }
}