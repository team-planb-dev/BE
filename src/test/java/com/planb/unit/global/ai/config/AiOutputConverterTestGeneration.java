package com.planb.unit.global.ai.config;

import com.planb.ai.dto.response.CreatePlanSelection;
import com.planb.global.config.ai.AiOutputConverterConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalTime;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiOutputConverterTestGeneration {

    @Test
    @DisplayName("생성 선택 스키마는 장소 메타데이터를 제거하고 선택적 사실값에 null 허용")
    void generationSchemaContainsOnlyModelDecisions() {

        BeanOutputConverter<CreatePlanSelection> converter = new AiOutputConverterConfig()
                .createPlanSelectionConverter();

        JsonNode schema = JsonMapper
                .builder()
                .build()
                .readTree(converter.getJsonSchema());

        String schemaText = schema.toString();

        assertThat(schemaText)
                .doesNotContain(
                        "locationName",
                        "longitude",
                        "latitude",
                        "imageUrl",
                        "medication",
                        "travelMinutes",
                        "carbohydrate",
                        "sodium",
                        "fat",
                        "openTime"
                );

        assertThat(findProperty(schema, "standardFoodName")).isNotNull();

        assertThat(findProperty(schema, "courseType")
                .toString())
                .doesNotContain("\"MEDICATION\"");

        for (String field : Set.of(
                "candidateId",
                "restaurantDetail"
        )) {
            JsonNode property = findProperty(schema, field);

            assertThat(property)
                    .as(field)
                    .isNotNull();

            assertThat(property
                    .get("type")
                    .toString())
                    .as(field)
                    .contains("null");
        }

        assertStrictObjects(schema);
    }

    @Test
    @DisplayName("선택 응답의 null 사실값과 일정 enum을 실제 변환기로 파싱")
    void parsesSelectionWithMissingOptionalFacts() {

        CreatePlanSelection selection = new AiOutputConverterConfig()
                .createPlanSelectionConverter()
                .convert("""
                        {
                          "planDays": [{
                            "dayNumber": 1,
                            "date": "2030-01-01",
                            "schedules": [{
                              "scheduleType": "ACTIVITY",
                              "courseType": "ATTRACTION",
                              "startTime": "09:00",
                              "endTime": "10:00",
                              "stayMinutes": 60,
                              "tags": [],
                              "restaurantDetail": null,
                              "candidateId": "tour:123"
                            }]
                          }]
                        }
                        """);

        assertThat(selection
                .planDays()
                .getFirst()
                .schedules()
                .getFirst()
                .startTime())
                .isEqualTo(LocalTime.of(9, 0));

        assertThat(selection
                .planDays()
                .getFirst()
                .schedules()
                .getFirst()
                .candidateId())
                .isEqualTo("tour:123");
    }

    private JsonNode findProperty(
            JsonNode node,
            String field
    ) {

        if (node == null) {
            return null;
        }

        JsonNode properties = node.get("properties");

        if (properties != null && properties.get(field) != null) {
            return properties.get(field);
        }

        for (JsonNode child : node) {
            JsonNode found = findProperty(child, field);

            if (found != null) {
                return found;
            }
        }

        return null;
    }

    private void assertStrictObjects(JsonNode node) {

        if (node == null) {
            return;
        }

        JsonNode properties = node.get("properties");

        if (properties != null && properties.isObject()) {
            Set<String> propertyNames = new HashSet<>();

            for (Map.Entry<String, JsonNode> entry : properties.properties()) {
                propertyNames.add(entry.getKey());
            }

            Set<String> requiredNames = new HashSet<>();

            for (JsonNode required : node.get("required")) {
                requiredNames.add(required.asText());
            }

            assertThat(requiredNames).containsExactlyInAnyOrderElementsOf(propertyNames);
            assertThat(node.get("additionalProperties").asBoolean()).isFalse();
        }

        for (JsonNode child : node) {
            assertStrictObjects(child);
        }
    }
}
