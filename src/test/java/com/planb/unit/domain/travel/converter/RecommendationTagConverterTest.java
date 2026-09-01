package com.planb.unit.domain.travel.converter;

import com.planb.domain.travel.converter.RecommendationTagConverter;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecommendationTagConverterTest {

    private final RecommendationTagConverter converter =
            new RecommendationTagConverter();

    @Test
    @DisplayName("RecommendationTag Enum DB 코드 변환")
    void convertToDatabaseColumn() {

        for (RecommendationTag recommendationTag :
                RecommendationTag.values()) {

            assertEquals(
                    recommendationTag.getCode(),
                    converter.convertToDatabaseColumn(
                            recommendationTag
                    )
            );
        }
    }

    @Test
    @DisplayName("DB 코드 RecommendationTag Enum 변환")
    void convertToEntityAttribute() {

        for (RecommendationTag recommendationTag :
                RecommendationTag.values()) {

            assertEquals(
                    recommendationTag,
                    converter.convertToEntityAttribute(
                            recommendationTag.getCode()
                    )
            );
        }
    }
}