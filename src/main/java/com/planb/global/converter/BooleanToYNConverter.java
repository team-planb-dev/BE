package com.planb.global.converter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

// Boolean 타입 필드를 DB에서 String타입으로 매핑
@Converter(autoApply = true)
public class BooleanToYNConverter implements AttributeConverter<Boolean,String> {

    // true,false 지정시 DB에는 Y,N ( String 타입 ) 으로 저장되는 컨버터 메소드
    @Override
    public String convertToDatabaseColumn(Boolean attribute) {
        return (attribute != null && attribute) ? "Y" : "N";
    }

    // SQL 조회시 , Y를 컬럼 조건으로 설정하면 객체의 boolean 필드가 true인 것을 반환
    @Override
    public Boolean convertToEntityAttribute(String dbData) {
        return "Y".equals(dbData);

    }
}
