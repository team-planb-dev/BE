package com.planb.global.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import com.planb.global.constant.enums.CodeCommInterface;
import com.planb.global.constant.enums.EnumUtil;

// Enum 타입 필드를 DB에서 객체이름으로 매핑
@Converter(autoApply = false)
public interface EnumConverter<T extends CodeCommInterface>
        extends AttributeConverter<T, String> {

    @Override
    default String convertToDatabaseColumn(T attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getCode();
    }

    @Override
    default T convertToEntityAttribute(String dbData) {
        return null;
    }

    default T convertToEntityAttribute(String dbData, Class<T> tClass) {
        return EnumUtil.findByCode(tClass, dbData);
    }
}
