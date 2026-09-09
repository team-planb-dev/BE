package com.planb.ai.context;
import com.planb.domain.health.entity.FoodInfo;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.MedicationInfo;
import com.planb.domain.health.entity.constant.*;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record TravelHealthContext(
        String travelerName,
        DiseaseType diseaseType,
        WalkType walkType,
        MealInfoContext mealInfo,
        List<FoodInfoContext> foodInfos,
        List<MedicationInfoContext> medicationInfos
) {

    public static TravelHealthContext from(
            Health health,
            List<FoodInfo> foodInfos,
            List<MedicationInfo> medicationInfos
    ) {

        return new TravelHealthContext(
                health.getTravelerName(),
                health.getHealthInfo().getDiseaseType(),
                health.getHealthInfo().getWalkType(),

                new MealInfoContext(
                        health.getMealInfo().isApplied(),
                        health.getMealInfo().isBreakfastApplied(),
                        health.getMealInfo().getBreakfastTime(),
                        health.getMealInfo().isLunchApplied(),
                        health.getMealInfo().getLunchTime(),
                        health.getMealInfo().isDinnerApplied(),
                        health.getMealInfo().getDinnerTime()
                ),

                foodInfos.stream()
                        .map(foodInfo ->
                                new FoodInfoContext(
                                        foodInfo.getFoodName(),
                                        foodInfo.getFoodType()
                                )
                        )
                        .toList(),

                medicationInfos.stream()
                        .map(MedicationInfoContext::from)
                        .toList()
        );
    }


    public record MealInfoContext(
            boolean applied,
            boolean breakfastApplied,
            LocalTime breakfastTime,
            boolean lunchApplied,
            LocalTime lunchTime,
            boolean dinnerApplied,
            LocalTime dinnerTime
    ) {

        public MealInfoContext(
                LocalTime breakfastTime,
                LocalTime lunchTime,
                LocalTime dinnerTime
        ) {

            this(
                    true,
                    breakfastTime != null,
                    breakfastTime,
                    lunchTime != null,
                    lunchTime,
                    dinnerTime != null,
                    dinnerTime
            );
        }
    }


    public record FoodInfoContext(
            String foodName,
            FoodType foodType
    ) {
    }


    public record MedicationInfoContext(
            String drugName,
            MedicationBasis medicationBasis,
            LocalTime medicationTime,
            Set<MealMedicationRuleContext> mealMedicationRules
    ) {

        private static MedicationInfoContext from(
                MedicationInfo medicationInfo
        ) {

            Set<MealMedicationRuleContext> rules =
                    medicationInfo.getMealMedicationRules()
                            .stream()
                            .map(rule ->
                                    new MealMedicationRuleContext(
                                            rule.getRelatedMeal(),
                                            rule.getMealTiming(),
                                            rule.getIntervalMinutes()
                                    )
                            )
                            .collect(Collectors.toSet());

            return new MedicationInfoContext(
                    medicationInfo.getDrugName(),
                    medicationInfo.getMedicationBasis(),
                    medicationInfo.getMedicationTime(),
                    rules
            );
        }


        public record MealMedicationRuleContext(
                RelatedMeal relatedMeal,
                MealTiming mealTiming,
                Integer intervalMinutes
        ) {
        }
    }
}