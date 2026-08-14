package com.planb.domain.health.dto.request;

import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.WalkType;


import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record AddCompanionRequest(
        String travelerName,
        boolean sensitiveAgree,
        boolean hasMedication,
        HealthInfo healthInfo,
        MealInfo mealInfo,
        List<FoodInfoDetail> foodInfoList,
        List<MedicationInfoDetail> medicationInfoList
) {

    /*
    내부 레코드 정의
     */
    public record HealthInfo(
            DiseaseType diseaseType,
            WalkType walkType
    ) {
    }

    public record MealInfo(
            boolean applied,
            boolean breakfastApplied,
            LocalTime breakfastTime,
            boolean lunchApplied,
            LocalTime lunchTime,
            boolean dinnerApplied,
            LocalTime dinnerTime
    ) {
    }

    public record FoodInfoDetail(
            String foodName,
            FoodType foodType
    ) {
    }

    public record MedicationInfoDetail(
            String drugName,
            MedicationBasis medicationBasis,
            LocalTime medicationTime,
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
