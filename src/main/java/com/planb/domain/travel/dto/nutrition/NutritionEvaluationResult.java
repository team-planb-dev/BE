package com.planb.domain.travel.dto.nutrition;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;

import java.util.List;

/**
 * 한 음식에 대한 영양성분 평가 결과.
 *
 * 여행자가 여러 질환을 관리하면 질환마다 보는 영양성분이 다르다. 평가 대상 음식은
 * 하나이고 원본 영양정보도 하나이므로, 질환별로 결과를 나누지 않고 평가 항목만 합쳐
 * 한 건으로 돌려준다.
 */
public record NutritionEvaluationResult(
        List<DiseaseType> diseaseTypes,
        NutritionEvaluationStatus status,
        List<NutritionEvaluationDetail> evaluations,
        Double carbohydrate,
        Double sodium,
        Double fat
) {
}
