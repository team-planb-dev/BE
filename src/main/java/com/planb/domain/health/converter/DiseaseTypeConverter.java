package com.planb.domain.health.converter;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class DiseaseTypeConverter
        implements EnumConverter<DiseaseType> {

    @Override
    public DiseaseType convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                DiseaseType.class
        );
    }
}
