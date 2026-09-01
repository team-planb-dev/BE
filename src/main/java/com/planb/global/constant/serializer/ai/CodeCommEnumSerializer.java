package com.planb.global.constant.serializer.ai;

import com.planb.global.constant.enums.CodeCommInterface;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

public class CodeCommEnumSerializer extends ValueSerializer<CodeCommInterface> {

    @Override
    public void serialize(CodeCommInterface value, JsonGenerator gen, SerializationContext ctxt) {
        if (value == null) {
            gen.writeNull();
        } else {
            gen.writeString(value.getCode());
        }
    }
}