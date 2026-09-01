package com.planb.global.constant.serializer.ai;

import com.planb.ai.dto.response.CreatePlanAiResponse.RestaurantDetail;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ValueDeserializer;

public class RestaurantDetailDeserializer extends ValueDeserializer<RestaurantDetail> {

    @Override
    public RestaurantDetail deserialize(JsonParser parser, DeserializationContext ctxt) {
        JsonNode node = parser.readValueAsTree();

        if (node == null || node.isNull()) {
            return null;
        }

        return new RestaurantDetail(
                asStringOrNull(node.get("menuName")),
                asDoubleOrNull(node.get("carbohydrate")),
                asDoubleOrNull(node.get("sodium")),
                asDoubleOrNull(node.get("fat")),
                asStringOrNull(node.get("openTime")),
                asStringOrNull(node.get("address")),
                asStringOrNull(node.get("longitude")),
                asStringOrNull(node.get("latitude")),
                asStringOrNull(node.get("imageUrl"))
        );
    }

    private String asStringOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asString();
    }

    private Double asDoubleOrNull(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return null;
        }
        return node.asDouble();
    }
}