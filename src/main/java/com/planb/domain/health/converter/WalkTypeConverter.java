package com.planb.domain.health.converter;


import com.planb.domain.health.entity.constant.WalkType;
import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class WalkTypeConverter
        implements EnumConverter<WalkType> {

    @Override
    public WalkType convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                WalkType.class
        );
    }
}