package com.planb.unit.domain.health.converter;

import com.planb.domain.health.converter.DiseaseTypeConverter;
import com.planb.domain.health.entity.constant.DiseaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DiseaseTypeConverterTest {

    private final DiseaseTypeConverter converter =
            new DiseaseTypeConverter();

    @Test
    @DisplayName("DiseaseType 코드 기반 Enum 변환")
    void convertToEntityAttributeSuccess() {

        // given
        DiseaseType expected =
                DiseaseType.values()[0];

        String code =
                expected.getCode();

        // when
        DiseaseType result =
                converter.convertToEntityAttribute(code);

        // then
        assertEquals(expected, result);
    }
}