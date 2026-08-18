package com.planb.domain.health.converter;

import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class MedicationBasisConverter
        implements EnumConverter<MedicationBasis> {

    @Override
    public MedicationBasis convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                MedicationBasis.class
        );
    }
}
