package com.planb.domain.health.dto.request;

import java.util.List;

/**
 * 동행인 정보의 전체 수정 요청
 * @param healthId 수정할 동행인 id
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

    // 등록 요청과 필드 구성이 같으므로 기존 변환 메소드를 재사용한다.
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
