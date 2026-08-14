package com.planb.unit.domain.health.converter;

import com.planb.domain.health.converter.WalkTypeConverter;
import com.planb.domain.health.entity.constant.WalkType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WalkTypeConverterTest {

    private final WalkTypeConverter converter =
            new WalkTypeConverter();

    @Test
    @DisplayName("WalkType 코드 기반 Enum 변환")
    void convertToEntityAttributeSuccess() {

        // given
        WalkType expected =
                WalkType.values()[0];

        String code =
                expected.getCode();

        // when
        WalkType result =
                converter.convertToEntityAttribute(code);

        // then
        assertEquals(expected, result);
    }
}