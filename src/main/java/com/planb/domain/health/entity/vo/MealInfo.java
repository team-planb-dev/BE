package com.planb.domain.health.entity.vo;

import com.planb.global.converter.BooleanToYNConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;


import java.time.LocalTime;

@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class MealInfo {

    // 평소 식사시간을 여행 일정에 반영할지 여부
    @Convert(converter = BooleanToYNConverter.class)
    @Column(
            name = "meal_applied",
            nullable = false,
            length = 1
    )
    private boolean applied;


    // 아침 식사시간 반영 여부
    @Convert(converter = BooleanToYNConverter.class)
    @Column(
            name = "breakfast_applied",
            nullable = false,
            length = 1
    )
    private boolean breakfastApplied;

    // 아침 식사시간
    @Column(name = "breakfast_time")
    private LocalTime breakfastTime;


    // 점심 식사시간 반영 여부
    @Convert(converter = BooleanToYNConverter.class)
    @Column(
            name = "lunch_applied",
            nullable = false,
            length = 1
    )
    private boolean lunchApplied;

    // 점심 식사시간
    @Column(name = "lunch_time")
    private LocalTime lunchTime;


    // 저녁 식사시간 반영 여부
    @Convert(converter = BooleanToYNConverter.class)
    @Column(
            name = "dinner_applied",
            nullable = false,
            length = 1
    )
    private boolean dinnerApplied;

    // 저녁 식사시간
    @Column(name = "dinner_time")
    private LocalTime dinnerTime;
}