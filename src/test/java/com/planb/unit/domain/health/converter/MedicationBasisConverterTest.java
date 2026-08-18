package com.planb.unit.domain.health.converter;

import com.planb.domain.health.converter.MedicationBasisConverter;
import com.planb.domain.health.entity.constant.MedicationBasis;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MedicationBasisConverterTest {

    private final MedicationBasisConverter converter =
            new MedicationBasisConverter();

    @Test
    @DisplayName("MedicationBasis 코드 기반 Enum 변환")
    void convertToEntityAttributeSuccess() {

        // given
        MedicationBasis expected =
                MedicationBasis.values()[0];

        String code =
                expected.getCode();

        // when
        MedicationBasis result =
                converter.convertToEntityAttribute(code);

        // then
        assertEquals(expected, result);
    }
}