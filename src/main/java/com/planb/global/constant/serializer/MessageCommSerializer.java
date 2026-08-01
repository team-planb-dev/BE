package com.planb.global.constant.serializer;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.planb.global.enums.MessageCommInterface;

import java.io.IOException;

public class MessageCommSerializer extends StdSerializer<MessageCommInterface> {

    public MessageCommSerializer() {
        this(null);
    }

    protected MessageCommSerializer(Class<MessageCommInterface> t) {
        super(t);
    }

    // API 호출 결과에 대해서
    @Override
    public void serialize(MessageCommInterface value,
                          JsonGenerator gen,
                          SerializerProvider provider)
            throws IOException {

        gen.writeStartObject();
        gen.writeStringField("code", value.getCode());
        gen.writeStringField("message", value.getMessage());
        gen.writeEndObject();
    }
}
