package com.planb.domain.travel.converter;

import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class ScheduleTypeConverter implements EnumConverter<ScheduleType> {

    @Override
    public ScheduleType convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                ScheduleType.class
        );
    }
}