package com.planb.unit.domain.health.converter;

import com.planb.domain.health.converter.RelatedMealConverter;
import com.planb.domain.health.entity.constant.RelatedMeal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RelatedMealConverterTest {

    private final RelatedMealConverter converter =
            new RelatedMealConverter();

    @Test
    @DisplayName("RelatedMeal 코드 기반 Enum 변환")
    void convertToEntityAttributeSuccess() {

        // given
        RelatedMeal expected =
                RelatedMeal.values()[0];

        String code =
                expected.getCode();

        // when
        RelatedMeal result =
                converter.convertToEntityAttribute(code);

        // then
        assertEquals(expected, result);
    }
}