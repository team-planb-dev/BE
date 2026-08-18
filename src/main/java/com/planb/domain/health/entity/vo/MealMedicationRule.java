package com.planb.domain.health.entity.vo;


import com.planb.domain.health.converter.MealTimingConverter;
import com.planb.domain.health.converter.RelatedMealConverter;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.RelatedMeal;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MealMedicationRule {

    // 아침 / 점심 / 저녁
    @Convert(converter = RelatedMealConverter.class)
    @Column(name = "related_meal")
    private RelatedMeal relatedMeal;

    // 식전 / 식사중 / 식후 / 무관
    @Convert(converter = MealTimingConverter.class)
    @Column(name = "meal_timing")
    private MealTiming mealTiming;

    // 간격 (분)
    @Column(name = "interval_minutes")
    private Integer intervalMinutes;
}
