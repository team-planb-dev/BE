package com.planb.unit.domain.travel.converter;

import com.planb.domain.travel.converter.TravelStyleConverter;
import com.planb.domain.travel.entity.constant.TravelStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TravelStyleConverterTest {

    private final TravelStyleConverter converter =
            new TravelStyleConverter();

    @Test
    @DisplayName("TravelStyle Enum DB 코드 변환")
    void convertToDatabaseColumn() {

        for (TravelStyle travelStyle : TravelStyle.values()) {

            assertEquals(
                    travelStyle.getCode(),
                    converter.convertToDatabaseColumn(travelStyle)
            );
        }
    }

    @Test
    @DisplayName("DB 코드 TravelStyle Enum 변환")
    void convertToEntityAttribute() {

        for (TravelStyle travelStyle : TravelStyle.values()) {

            assertEquals(
                    travelStyle,
                    converter.convertToEntityAttribute(
                            travelStyle.getCode()
                    )
            );
        }
    }
}