package com.planb.domain.health.dto.request;

import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.RelatedMeal;

public record MealMedicationRuleDetail(RelatedMeal relatedMeal,
                                       MealTiming mealTiming,
                                       Integer intervalMinutes) {

}