package com.planb.domain.health.converter;

import com.planb.domain.health.entity.constant.FoodType;
import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class FoodTypeConverter
        implements EnumConverter<FoodType> {

    @Override
    public FoodType convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                FoodType.class
        );
    }
}
