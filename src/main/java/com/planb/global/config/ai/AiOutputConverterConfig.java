package com.planb.global.config.ai;

import com.planb.ai.dto.response.CreatePlanAiResponse;
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
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalTime;

@Configuration
public class AiOutputConverterConfig {

    @Bean
    public BeanOutputConverter<CreatePlanAiResponse> createPlanAiResponseConverter() {
        SimpleModule module = new SimpleModule();
        module.addSerializer(CodeCommInterface.class, new CodeCommEnumSerializer());
        module.addDeserializer(ScheduleType.class, new CodeCommEnumDeserializer<>(ScheduleType.class));
        module.addDeserializer(CourseType.class, new CodeCommEnumDeserializer<>(CourseType.class));
        module.addDeserializer(RecommendationTag.class, new CodeCommEnumDeserializer<>(RecommendationTag.class));
        module.addDeserializer(LocalTime.class, new LenientLocalTimeDeserializer());
        module.addDeserializer(CreatePlanAiResponse.RestaurantDetail.class, new RestaurantDetailDeserializer());

        JsonMapper jsonMapper = JsonMapper.builder()
                .addModule(module)
                .build();

        return new BeanOutputConverter<>(CreatePlanAiResponse.class, jsonMapper);
    }
}