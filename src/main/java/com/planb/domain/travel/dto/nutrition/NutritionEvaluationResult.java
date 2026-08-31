package com.planb.domain.travel.dto.nutrition;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;

import java.util.List;

public record NutritionEvaluationResult(
        DiseaseType diseaseType,
        NutritionEvaluationStatus status,
        List<NutritionEvaluationDetail> evaluations,
        Double carbohydrate,
        Double sodium,
        Double fat
) {
}