package com.planb.domain.travel.converter;

import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.global.converter.EnumConverter;
import jakarta.persistence.Converter;

@Converter
public class RecommendationTagConverter implements EnumConverter<RecommendationTag> {

    @Override
    public RecommendationTag convertToEntityAttribute(String dbData) {

        return convertToEntityAttribute(
                dbData,
                RecommendationTag.class
        );
    }
}



