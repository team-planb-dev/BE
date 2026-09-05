package com.planb.global.constant.serializer;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.planb.global.constant.enums.CodeCommInterface;
import com.planb.global.constant.enums.EnumUtil;

import java.io.IOException;

public class CodeCommDeserializer<T extends CodeCommInterface>
        extends JsonDeserializer<T>
        implements ContextualDeserializer {

    // CodeCommDeserializer 클래스에 대해 로그 찍기
    private static final Logger log = LoggerFactory.getLogger(CodeCommDeserializer.class);
    private Class<T> targetClass; // 런타입에 createContextual()메소드를 통해 채워지는 필드

    // 역직력화 메소드
    public T deserialize
    (JsonParser jsonParser, DeserializationContext ctxt)
            throws IOException {
        JsonNode jsonNode = (JsonNode)jsonParser.getCodec().readTree(jsonParser);
        if (jsonNode.asText().length() == 1) {
            // 길이가 1이면 단순 코드값으로 판단하고 바로 findByCode 호출
            return (T) EnumUtil.findByCode(this.targetClass, jsonNode.asText());
        } else {
            String reqCode;
            if (jsonNode.get("code") == null) {
                // code 필드가 존재하는지 확인하고 없으면 null
                reqCode = jsonNode.asText();
            } else {
                // 있으면 해당 노드를 반환
                reqCode = jsonNode.get("code").asText();
            }

            // reqCode ( code ) 를 사용해 CodeName을 가져오기
            return (T) EnumUtil.findByCode(this.targetClass, reqCode);
        }
    }

    @SuppressWarnings("unchecked")
    public JsonDeserializer<T> createContextual
            (DeserializationContext ctxt, BeanProperty property)
            throws JsonMappingException {
        String targetClassName = ctxt.getContextualType().toCanonical();

        try {
            this.targetClass = (Class<T>) Class.forName(targetClassName);
            // 문자열을 실제 class 객체로 변환
        } catch (ClassNotFoundException e) {
            log.error("ERROR : ", e);
        }

        return this;
    }

}
