package com.planb.domain.travel.dto.nutrition;

import com.planb.domain.travel.entity.constant.NutritionLevel;
import com.planb.domain.travel.entity.constant.NutritionType;

public record NutritionEvaluationDetail(
        NutritionType nutritionType,
        NutritionLevel nutritionLevel
) {
}