package com.planb.unit.domain.travel.converter;

import com.planb.domain.travel.converter.TravelThemeConverter;
import com.planb.domain.travel.entity.constant.TravelTheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TravelThemeConverterTest {

    private final TravelThemeConverter converter =
            new TravelThemeConverter();

    @Test
    @DisplayName("TravelTheme Enum DB 코드 변환")
    void convertToDatabaseColumn() {

        for (TravelTheme travelTheme : TravelTheme.values()) {

            assertEquals(
                    travelTheme.getCode(),
                    converter.convertToDatabaseColumn(travelTheme)
            );
        }
    }

    @Test
    @DisplayName("DB 코드 TravelTheme Enum 변환")
    void convertToEntityAttribute() {

        for (TravelTheme travelTheme : TravelTheme.values()) {

            assertEquals(
                    travelTheme,
                    converter.convertToEntityAttribute(
                            travelTheme.getCode()
                    )
            );
        }
    }
}