package com.planb.unit.domain.travel.converter;

import com.planb.domain.travel.converter.ScheduleTypeConverter;
import com.planb.domain.travel.entity.constant.ScheduleType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScheduleTypeConverterTest {

    private final ScheduleTypeConverter converter =
            new ScheduleTypeConverter();

    @Test
    @DisplayName("ScheduleType Enum DB 코드 변환")
    void convertToDatabaseColumn() {

        for (ScheduleType scheduleType : ScheduleType.values()) {

            assertEquals(
                    scheduleType.getCode(),
                    converter.convertToDatabaseColumn(scheduleType)
            );
        }
    }

    @Test
    @DisplayName("DB 코드 ScheduleType Enum 변환")
    void convertToEntityAttribute() {

        for (ScheduleType scheduleType : ScheduleType.values()) {

            assertEquals(
                    scheduleType,
                    converter.convertToEntityAttribute(
                            scheduleType.getCode()
                    )
            );
        }
    }
}