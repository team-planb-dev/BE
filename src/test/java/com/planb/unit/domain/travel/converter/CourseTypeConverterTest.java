package com.planb.unit.domain.travel.converter;

import com.planb.domain.travel.converter.CourseTypeConverter;
import com.planb.domain.travel.entity.constant.CourseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CourseTypeConverterTest {

    private final CourseTypeConverter converter =
            new CourseTypeConverter();

    @Test
    @DisplayName("CourseType Enum DB 코드 변환")
    void convertToDatabaseColumn() {

        for (CourseType courseType : CourseType.values()) {

            assertEquals(
                    courseType.getCode(),
                    converter.convertToDatabaseColumn(courseType)
            );
        }
    }

    @Test
    @DisplayName("DB 코드 CourseType Enum 변환")
    void convertToEntityAttribute() {

        for (CourseType courseType : CourseType.values()) {

            assertEquals(
                    courseType,
                    converter.convertToEntityAttribute(
                            courseType.getCode()
                    )
            );
        }
    }
}