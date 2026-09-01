package com.planb.domain.travel.converter;

import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class CourseTypeConverter implements EnumConverter<CourseType> {

    @Override
    public CourseType convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                CourseType.class
        );
    }
}



