package com.planb.global.constant.serializer.ai;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.time.LocalTime;

public class LenientLocalTimeDeserializer extends ValueDeserializer<LocalTime> {

    @Override
    public LocalTime deserialize(JsonParser parser, DeserializationContext ctxt) {
        JsonNode node = parser.readValueAsTree();

        if (node == null || node.isNull()) {
            return null;
        }

        String text = node.asString();

        if (text == null || text.isBlank() || "null".equalsIgnoreCase(text.trim())) {
            return null;
        }

        return LocalTime.parse(text.trim());
    }
}