package com.planb.domain.travel.entity.constant;

import com.planb.global.constant.enums.CodeCommInterface;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum NutritionEvaluationStatus implements CodeCommInterface {

    AVAILABLE(
            "AVAILABLE",
            "영양정보 조회 완료"
    ),

    UNAVAILABLE(
            "UNAVAILABLE",
            "영양정보 조회 불가"
    ),

    NOT_EVALUABLE(
            "NOT_EVALUABLE",
            "영양정보 평가 불가"
    );

    private final String code;
    private final String codeName;
}