package com.planb.global.config.ai;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.CreatePlanSelection;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.global.constant.enums.CodeCommInterface;
import com.planb.global.constant.serializer.ai.CodeCommEnumDeserializer;
import com.planb.global.constant.serializer.ai.CodeCommEnumSerializer;
import com.planb.global.constant.serializer.ai.LenientLocalTimeDeserializer;
import com.planb.global.constant.serializer.ai.RestaurantDetailDeserializer;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.LocalTime;
import java.util.Set;

@Configuration
public class AiOutputConverterConfig {

    private static final Set<String> GENERATION_NULLABLE_FIELDS = Set.of(
            "candidateId",
            "restaurantDetail"
    );

    @Bean
    public BeanOutputConverter<CreatePlanSelection> createPlanSelectionConverter() {

        JsonMapper mapper = planJsonMapper();

        return new BeanOutputConverter<>(CreatePlanSelection.class, mapper) {

            @Override
            public String getJsonSchema() {

                JsonNode schema = mapper.readTree(super.getJsonSchema());

                addGenerationNullTypes(schema, mapper);

                return schema.toString();
            }
        };
    }

    private void addGenerationNullTypes(
            JsonNode node,
            JsonMapper mapper
    ) {

        if (node == null || !node.isObject()) {
            return;
        }

        JsonNode properties = node.get("properties");

        if (properties != null && properties.isObject()) {
            properties
                    .properties()
                    .forEach(entry -> {
                        if ("courseType".equals(entry.getKey())
                                && entry.getValue().isObject()) {
                            removeMedicationCourseType((ObjectNode) entry.getValue());
                        }

                        if (GENERATION_NULLABLE_FIELDS.contains(entry.getKey())
                                && entry.getValue().isObject()) {
                            addNullType((ObjectNode) entry.getValue(), mapper);
                        }
                    });
        }

        node.forEach(child -> addGenerationNullTypes(child, mapper));
    }

    private void addNullType(
            ObjectNode field,
            JsonMapper mapper
    ) {

        JsonNode type = field.get("type");

        if (type == null) {
            return;
        }

        ArrayNode nullable = mapper.createArrayNode();
        boolean alreadyNullable = false;

        if (type.isArray()) {
            for (JsonNode value : type) {
                nullable.add(value);

                if ("null".equals(value.asText())) {
                    alreadyNullable = true;
                }
            }
        } else {
            nullable.add(type);
        }

        if (!alreadyNullable) {
            nullable.add("null");
        }

        field.set("type", nullable);
    }

    private void removeMedicationCourseType(ObjectNode field) {

        JsonNode values = field.get("enum");

        if (values instanceof ArrayNode enumValues) {
            for (int index = enumValues.size() - 1; index >= 0; index--) {
                if ("MEDICATION".equals(enumValues.get(index).asText())) {
                    enumValues.remove(index);
                }
            }
        }
    }

    @Bean
    public BeanOutputConverter<EditPlanAiResponse> editPlanAiResponseConverter() {

        return new BeanOutputConverter<>(EditPlanAiResponse.class, planJsonMapper());
    }

    @Bean
    public BeanOutputConverter<RebuildPlanDayResponse> rebuildPlanDayResponseConverter() {

        return new BeanOutputConverter<>(RebuildPlanDayResponse.class, planJsonMapper());
    }

    private JsonMapper planJsonMapper() {

        SimpleModule module = new SimpleModule();

        module
                .addSerializer(CodeCommInterface.class, new CodeCommEnumSerializer());

        module
                .addDeserializer(ScheduleType.class, new CodeCommEnumDeserializer<>(ScheduleType.class));

        module
                .addDeserializer(CourseType.class, new CodeCommEnumDeserializer<>(CourseType.class));

        module
                .addDeserializer(RecommendationTag.class, new CodeCommEnumDeserializer<>(RecommendationTag.class));

        module
                .addDeserializer(LocalTime.class, new LenientLocalTimeDeserializer());

        module
                .addDeserializer(CreatePlanAiResponse.RestaurantDetail.class, new RestaurantDetailDeserializer());

        return JsonMapper
                .builder()
                .addModule(module)
                .build();
    }
}
