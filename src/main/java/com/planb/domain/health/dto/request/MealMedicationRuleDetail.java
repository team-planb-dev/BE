package com.planb.domain.health.dto.request;

import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.RelatedMeal;
import io.swagger.v3.oas.annotations.media.Schema;

public record MealMedicationRuleDetail(
        @Schema(description = "복약 기준이 되는 식사", example = "LUNCH")
        RelatedMeal relatedMeal,

        @Schema(description = "식사 전후 복약 시점", example = "AFTER_MEAL")
        MealTiming mealTiming,

        @Schema(description = "식사 시각과 복약 시각 사이 간격(분)", example = "30")
        Integer intervalMinutes
) {

}
