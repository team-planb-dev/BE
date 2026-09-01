package com.planb.domain.travel.converter;

import com.planb.domain.travel.entity.constant.DateType;

import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class DateTypeConverter implements EnumConverter<DateType> {

    @Override
    public DateType convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                DateType.class
        );
    }
}