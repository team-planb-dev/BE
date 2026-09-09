package com.planb.domain.health.dto.request;

import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.WalkType;
import io.swagger.v3.oas.annotations.media.Schema;


import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record AddCompanionRequest(
        @Schema(description = "동행인 이름", example = "홍길동")
        String travelerName,

        @Schema(description = "민감 건강정보 수집 동의 여부", example = "true")
        boolean sensitiveAgree,

        @Schema(description = "복약 정보 등록 여부", example = "true")
        boolean hasMedication,

        @Schema(description = "질환과 걷기 선호 정보")
        HealthInfo healthInfo,

        @Schema(description = "일정에 적용할 식사 시간 정보")
        MealInfo mealInfo,

        @Schema(description = "알레르기 또는 기피 음식 목록")
        List<FoodInfoDetail> foodInfoList,

        @Schema(description = "복약 정보 목록")
        List<MedicationInfoDetail> medicationInfoList
) {

    /*
    내부 레코드 정의
     */
    public record HealthInfo(
            @Schema(description = "질환 유형", example = "DIABETES")
            DiseaseType diseaseType,

            @Schema(description = "걷기 선호 수준", example = "MODERATE")
            WalkType walkType
    ) {
    }

    public record MealInfo(
            @Schema(description = "식사 시간 정보를 일정에 적용할지 여부", example = "true")
            boolean applied,

            @Schema(description = "아침 식사 시간 적용 여부", example = "true")
            boolean breakfastApplied,

            @Schema(description = "아침 식사 기준 시각", example = "08:00")
            LocalTime breakfastTime,

            @Schema(description = "점심 식사 시간 적용 여부", example = "true")
            boolean lunchApplied,

            @Schema(description = "점심 식사 기준 시각", example = "12:00")
            LocalTime lunchTime,

            @Schema(description = "저녁 식사 시간 적용 여부", example = "true")
            boolean dinnerApplied,

            @Schema(description = "저녁 식사 기준 시각", example = "18:00")
            LocalTime dinnerTime
    ) {
    }

    public record FoodInfoDetail(
            @Schema(description = "구체적인 음식 또는 재료 이름", example = "새우")
            String foodName,

            @Schema(description = "음식 제한 유형", example = "ALLERGY")
            FoodType foodType
    ) {
    }

    public record MedicationInfoDetail(
            @Schema(description = "약 이름", example = "혈압약")
            String drugName,

            @Schema(description = "복약 시간 기준", example = "WITH_MEAL")
            MedicationBasis medicationBasis,

            @Schema(description = "고정 복약 시각입니다. 식사 기준 복약이면 null일 수 있습니다.", example = "12:30")
            LocalTime medicationTime,

            @Schema(description = "식사 기준 복약 규칙 목록")
            Set<MealMedicationRuleDetail> mealMedicationRuleDetails
    ) {

    }

        /*
        내부 파싱 메소드 정리
         */
        public CreateHealthRequest toHealthRequest() {

            return new CreateHealthRequest(
                    travelerName,
                    sensitiveAgree,
                    hasMedication,
                    new CreateHealthRequest.HealthInfo(
                            healthInfo.diseaseType(),
                            healthInfo.walkType()
                    ),
                    new CreateHealthRequest.MealInfo(
                            mealInfo.applied(),
                            mealInfo.breakfastApplied(),
                            mealInfo.breakfastTime(),
                            mealInfo.lunchApplied(),
                            mealInfo.lunchTime(),
                            mealInfo.dinnerApplied(),
                            mealInfo.dinnerTime()
                    )
            );
        }

        public CreateFoodInfoRequest toFoodInfoRequest(Health health) {

            List<CreateFoodInfoRequest.FoodInfoDetail> data =
                    foodInfoList.stream()
                            .map(food ->
                                    new CreateFoodInfoRequest.FoodInfoDetail(
                                            food.foodName(),
                                            food.foodType()
                                    )
                            )
                            .toList();

            return new CreateFoodInfoRequest(
                    health,
                    data
            );
        }

        public CreateMedicationInfoRequest toMedicationInfoRequest(
                Health health
        ) {

            List<CreateMedicationInfoRequest.MedicationInfoDetail> data =
                    medicationInfoList.stream()
                            .map(medication ->
                                    new CreateMedicationInfoRequest.MedicationInfoDetail(
                                            medication.drugName(),
                                            medication.medicationBasis(),
                                            medication.medicationTime(),
                                            medication.mealMedicationRuleDetails()
                                    )
                            )
                            .toList();

            return new CreateMedicationInfoRequest(
                    health,
                    data
            );
        }
}
