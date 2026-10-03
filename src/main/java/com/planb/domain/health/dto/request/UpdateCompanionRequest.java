package com.planb.domain.health.dto.request;

import java.util.List;

/**
 * 동행인 정보의 전체 수정 요청
 */
public record UpdateCompanionRequest(

        Long healthId,
        String travelerName,
        boolean sensitiveAgree,
        boolean hasMedication,
        AddCompanionRequest.HealthInfo healthInfo,
        AddCompanionRequest.MealInfo mealInfo,
        List<AddCompanionRequest.FoodInfoDetail> foodInfoList,
        List<AddCompanionRequest.MedicationInfoDetail> medicationInfoList
) {

    // 동일 필드 구성의 등록 요청 변환 메서드 재사용
    public AddCompanionRequest toAddCompanionRequest() {

        return new AddCompanionRequest(
                travelerName,
                sensitiveAgree,
                hasMedication,
                healthInfo,
                mealInfo,
                foodInfoList,
                medicationInfoList
        );
    }
}
