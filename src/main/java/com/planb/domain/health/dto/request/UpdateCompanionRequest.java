package com.planb.domain.health.dto.request;

import java.util.List;

/**
 * 동행인 수정 요청.
 *
 * 항목별 부분 수정 대신 화면에서 편집한 전체 값을 그대로 받아 덮어쓴다.
 * 음식/복약 정보는 개수가 바뀔 수 있어 항목 단위 매칭보다 전체 교체가 단순하다.
 *
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
