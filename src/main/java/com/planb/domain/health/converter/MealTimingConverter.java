package com.planb.domain.health.converter;

import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.global.converter.EnumConverter;

public class MealTimingConverter implements EnumConverter<MealTiming> {

    @Override
    public MealTiming convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                MealTiming.class
        );
    }
}