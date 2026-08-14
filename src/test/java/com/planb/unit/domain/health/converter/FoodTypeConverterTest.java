package com.planb.unit.domain.health.converter;

import com.planb.domain.health.converter.FoodTypeConverter;
import com.planb.domain.health.entity.constant.FoodType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FoodTypeConverterTest {

    private final FoodTypeConverter converter =
            new FoodTypeConverter();

    @Test
    @DisplayName("FoodType 코드 기반 Enum 변환")
    void convertToEntityAttributeSuccess() {

        // given
        FoodType expected =
                FoodType.values()[0];

        String code =
                expected.getCode();

        // when
        FoodType result =
                converter.convertToEntityAttribute(code);

        // then
        assertEquals(expected, result);
    }
}