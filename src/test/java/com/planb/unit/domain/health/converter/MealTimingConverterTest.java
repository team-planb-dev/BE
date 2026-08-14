package com.planb.unit.domain.health.converter;

import com.planb.domain.health.converter.MealTimingConverter;
import com.planb.domain.health.entity.constant.MealTiming;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MealTimingConverterTest {

    private final MealTimingConverter converter =
            new MealTimingConverter();

    @Test
    @DisplayName("MealTiming 코드 기반 Enum 변환")
    void convertToEntityAttributeSuccess() {

        // given
        MealTiming expected =
                MealTiming.values()[0];

        String code =
                expected.getCode();

        // when
        MealTiming result =
                converter.convertToEntityAttribute(code);

        // then
        assertEquals(expected, result);
    }
}