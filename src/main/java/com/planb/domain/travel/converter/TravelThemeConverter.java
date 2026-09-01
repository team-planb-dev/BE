package com.planb.domain.travel.converter;

import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class TravelThemeConverter implements EnumConverter<TravelTheme> {

    @Override
    public TravelTheme convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                TravelTheme.class
        );
    }
}

