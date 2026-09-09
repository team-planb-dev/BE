package com.planb.global.constant.serializer.ai;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

import java.time.LocalTime;

public class LenientLocalTimeDeserializer extends ValueDeserializer<LocalTime> {

    private static final int MIN_TIME_ARRAY_SIZE = 2;

    @Override
    public LocalTime deserialize(JsonParser parser, DeserializationContext ctxt) {
        JsonNode node = parser.readValueAsTree();

        if (node == null || node.isNull()) {
            return null;
        }

        if (node.isArray()) {
            return parseArray(node);
        }

        String text = node.asString();

        if (text == null || text.isBlank() || "null".equalsIgnoreCase(text.trim())) {
            return null;
        }

        return LocalTime.parse(text.trim());
    }

    // [시, 분] 또는 [시, 분, 초] 배열 형태 시간 파싱 (currentPlan 원본 배열 표현을 AI가 그대로 echo하는 경우 대응)
    private LocalTime parseArray(JsonNode node) {

        if (node.size() < MIN_TIME_ARRAY_SIZE) {
            return null;
        }

        int hour = node.get(0).asInt();
        int minute = node.get(1).asInt();
        int second = node.size() > 2 ? node.get(2).asInt() : 0;

        return LocalTime.of(hour, minute, second);
    }
}
