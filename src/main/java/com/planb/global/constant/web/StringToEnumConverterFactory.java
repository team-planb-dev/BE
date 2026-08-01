package com.planb.global.constant.web;


import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;
import com.planb.global.constant.enums.CodeCommInterface;
import com.planb.global.constant.enums.EnumUtil;

final public class StringToEnumConverterFactory
        implements ConverterFactory<String, CodeCommInterface> {

    @Override
    public <T extends CodeCommInterface> Converter<String, T> getConverter(Class<T> targetType) {
        return new StringToEnumConverter<>(targetType);
    }

    private record StringToEnumConverter<T extends CodeCommInterface>(
            Class<T> targetClass) implements Converter<String, T> {

        public T convert(String source) {
            return EnumUtil.findByCode(this.targetClass, source);
        }
    }


}
