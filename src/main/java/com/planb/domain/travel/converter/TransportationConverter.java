package com.planb.domain.travel.converter;

import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class TransportationConverter implements EnumConverter<Transportation> {

    @Override
    public Transportation convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                Transportation.class
        );
    }
}
