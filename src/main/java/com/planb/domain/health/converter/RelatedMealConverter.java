package com.planb.domain.health.converter;

import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class RelatedMealConverter
        implements EnumConverter<RelatedMeal> {

    @Override
    public RelatedMeal convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                RelatedMeal.class
        );
    }
}
