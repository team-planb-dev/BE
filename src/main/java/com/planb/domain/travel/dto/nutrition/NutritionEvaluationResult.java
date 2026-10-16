package com.planb.domain.travel.dto.nutrition;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;

import java.util.List;

/**
 * 음식 한 건에 대한 질환별 영양 평가 결과
 */
public record NutritionEvaluationResult(
        List<DiseaseType> diseaseTypes,
        NutritionEvaluationStatus status,
        List<NutritionEvaluationDetail> evaluations,
        Double carbohydrate,
        Double sodium,
        Double fat,
        String nutritionFoodName,
        String nutritionReferenceAmount,
        String nutritionSource,
        String lookupName
) {

    public NutritionEvaluationResult(
            List<DiseaseType> diseaseTypes,
            NutritionEvaluationStatus status,
            List<NutritionEvaluationDetail> evaluations,
            Double carbohydrate,
            Double sodium,
            Double fat,
            String nutritionFoodName,
            String nutritionReferenceAmount,
            String nutritionSource
    ) {

        this(
                diseaseTypes,
                status,
                evaluations,
                carbohydrate,
                sodium,
                fat,
                nutritionFoodName,
                nutritionReferenceAmount,
                nutritionSource,
                null
        );
    }

    public NutritionEvaluationResult(
            List<DiseaseType> diseaseTypes,
            NutritionEvaluationStatus status,
            List<NutritionEvaluationDetail> evaluations,
            Double carbohydrate,
            Double sodium,
            Double fat
    ) {

        this(
                diseaseTypes,
                status,
                evaluations,
                carbohydrate,
                sodium,
                fat,
                null,
                null,
                null,
                null
        );
    }
}
