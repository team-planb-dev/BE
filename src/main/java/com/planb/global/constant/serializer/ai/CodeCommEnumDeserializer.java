package com.planb.global.constant.serializer.ai;

import com.planb.global.constant.enums.CodeCommInterface;
import com.planb.global.constant.enums.EnumUtil;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class CodeCommEnumDeserializer<T extends Enum<T> & CodeCommInterface>
        extends ValueDeserializer<T> {

    private final Class<T> targetClass;

    public CodeCommEnumDeserializer(Class<T> targetClass) {
        this.targetClass = targetClass;
    }

    @Override
    public T deserialize(JsonParser parser, DeserializationContext ctxt) {
        JsonNode node = parser.readValueAsTree();

        JsonNode codeNode = node.get("code");
        String reqCode = (codeNode != null) ? codeNode.asString() : node.asString();

        return EnumUtil.findByCode(targetClass, reqCode);
    }
}