package com.planb.unit.domain.travel.converter;

import com.planb.domain.travel.converter.DateTypeConverter;
import com.planb.domain.travel.entity.constant.DateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DateTypeConverterTest {

    private final DateTypeConverter converter =
            new DateTypeConverter();

    @Test
    @DisplayName("DateType Enum DB 코드 변환")
    void convertToDatabaseColumn() {

        for (DateType dateType : DateType.values()) {

            assertEquals(
                    dateType.getCode(),
                    converter.convertToDatabaseColumn(dateType)
            );
        }
    }

    @Test
    @DisplayName("DB 코드 DateType Enum 변환")
    void convertToEntityAttribute() {

        for (DateType dateType : DateType.values()) {

            assertEquals(
                    dateType,
                    converter.convertToEntityAttribute(
                            dateType.getCode()
                    )
            );
        }
    }
}