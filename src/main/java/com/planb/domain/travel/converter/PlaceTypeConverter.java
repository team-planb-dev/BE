package com.planb.domain.travel.converter;

import com.planb.domain.travel.entity.constant.PlaceType;
import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class PlaceTypeConverter implements EnumConverter<PlaceType> {

    @Override
    public PlaceType convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                PlaceType.class
        );
    }
}
