package com.planb.unit.domain.travel.converter;

import com.planb.domain.travel.converter.TransportationConverter;
import com.planb.domain.travel.entity.constant.Transportation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TransportationConverterTest {

    private final TransportationConverter converter =
            new TransportationConverter();

    @Test
    @DisplayName("Transportation Enum DB 코드 변환")
    void convertToDatabaseColumn() {

        for (Transportation transportation :
                Transportation.values()) {

            assertEquals(
                    transportation.getCode(),
                    converter.convertToDatabaseColumn(
                            transportation
                    )
            );
        }
    }

    @Test
    @DisplayName("DB 코드 Transportation Enum 변환")
    void convertToEntityAttribute() {

        for (Transportation transportation :
                Transportation.values()) {

            assertEquals(
                    transportation,
                    converter.convertToEntityAttribute(
                            transportation.getCode()
                    )
            );
        }
    }
}