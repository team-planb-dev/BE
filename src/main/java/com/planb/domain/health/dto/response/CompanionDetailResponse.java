package com.planb.domain.health.dto.response;

import com.planb.domain.health.entity.FoodInfo;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.MedicationInfo;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.constant.WalkType;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

/**
 * 동행인 수정 화면을 채우기 위한 상세 조회 결과.
 *
 * 민감정보에 동의하지 않은 동행인은 healthInfo, mealInfo가 null이고 목록은 비어 있다.
 */
public record CompanionDetailResponse(

        Long healthId,
        String travelerName,
        boolean sensitiveAgree,
        boolean hasMedication,
        HealthInfoDetail healthInfo,
        MealInfoDetail mealInfo,
        List<FoodInfoDetail> foodInfoList,
        List<MedicationInfoDetail> medicationInfoList
) {

    public record HealthInfoDetail(

            DiseaseType diseaseType,
            WalkType walkType) {
    }

    public record MealInfoDetail(

            boolean applied,
            boolean breakfastApplied,
            LocalTime breakfastTime,
            boolean lunchApplied,
            LocalTime lunchTime,
            boolean dinnerApplied,
            LocalTime dinnerTime) {
    }

    public record FoodInfoDetail(

            String foodName,
            FoodType foodType) {
    }

    public record MedicationInfoDetail(

            String drugName,
            MedicationBasis medicationBasis,
            LocalTime medicationTime,
            Set<MealMedicationRuleDetail> mealMedicationRules) {
    }

    public record MealMedicationRuleDetail(

            RelatedMeal relatedMeal,
            MealTiming mealTiming,
            Integer intervalMinutes) {
    }

    public static CompanionDetailResponse of(
            Health health,
            List<FoodInfo> foodInfoList,
            List<MedicationInfo> medicationInfoList
    ) {

        return new CompanionDetailResponse(
                health
                        .getId(),
                health
                        .getTravelerName(),
                health
                        .isSensitiveAgree(),
                health
                        .isHasMedication(),
                toHealthInfoDetail(health),
                toMealInfoDetail(health),
                foodInfoList
                        .stream()
                        .map(foodInfo -> new FoodInfoDetail(
                                foodInfo
                                        .getFoodName(),
                                foodInfo
                                        .getFoodType()
                        ))
                        .toList(),
                medicationInfoList
                        .stream()
                        .map(CompanionDetailResponse::toMedicationInfoDetail)
                        .toList()
        );
    }

    private static HealthInfoDetail toHealthInfoDetail(Health health) {

        if (health.getHealthInfo() == null) {
            return null;
        }

        return new HealthInfoDetail(
                health
                        .getHealthInfo()
                        .getDiseaseType(),
                health
                        .getHealthInfo()
                        .getWalkType()
        );
    }

    private static MealInfoDetail toMealInfoDetail(Health health) {

        if (health.getMealInfo() == null) {
            return null;
        }

        return new MealInfoDetail(
                health
                        .getMealInfo()
                        .isApplied(),
                health
                        .getMealInfo()
                        .isBreakfastApplied(),
                health
                        .getMealInfo()
                        .getBreakfastTime(),
                health
                        .getMealInfo()
                        .isLunchApplied(),
                health
                        .getMealInfo()
                        .getLunchTime(),
                health
                        .getMealInfo()
                        .isDinnerApplied(),
                health
                        .getMealInfo()
                        .getDinnerTime()
        );
    }

    private static MedicationInfoDetail toMedicationInfoDetail(MedicationInfo medicationInfo) {

        Set<MealMedicationRuleDetail> rules = medicationInfo
                .getMealMedicationRules() == null
                ? Set.of()
                : medicationInfo
                        .getMealMedicationRules()
                        .stream()
                        .map(rule -> new MealMedicationRuleDetail(
                                rule
                                        .getRelatedMeal(),
                                rule
                                        .getMealTiming(),
                                rule
                                        .getIntervalMinutes()
                        ))
                        .collect(java.util.stream.Collectors.toSet());

        return new MedicationInfoDetail(
                medicationInfo
                        .getDrugName(),
                medicationInfo
                        .getMedicationBasis(),
                medicationInfo
                        .getMedicationTime(),
                rules
        );
    }
}
