package com.planb.domain.travel.converter;

import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class TravelStyleConverter implements EnumConverter<TravelStyle> {

    @Override
    public TravelStyle convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                TravelStyle.class
        );
    }
}


