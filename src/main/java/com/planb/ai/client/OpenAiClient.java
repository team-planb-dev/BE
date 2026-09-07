package com.planb.ai.client;

import com.planb.ai.prompt.AiPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.util.Set;
import java.util.function.Predicate;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiClient {

    private final ChatClient chatClient;

    // JSON Schema 후처리 전용 매퍼, 도메인 커스텀 모듈 불필요
    private static final JsonMapper SCHEMA_MAPPER = JsonMapper.builder().build();

    // courseType에 따라 값이 없어야 정상인, strict 스키마에서도 null을 허용해야 하는 필드
    private static final Set<String> NULLABLE_SCHEDULE_FIELDS = Set.of(
            "candidateId",
            "locationName",
            "location",
            "longitude",
            "latitude",
            "imageUrl",
            "thumbNailImageUrl",
            "medication",
            "restaurantDetail"
    );

    // strict schema에서 제거할 format 값, RFC3339 해석(타임존·밀리초 포함) 강제 방지
    private static final Set<String> STRICT_UNSAFE_FORMATS = Set.of("date", "time");

    // 기본 호출
    public <T> T call(
            AiPrompt prompt,
            Class<T> responseType) {

        return call(prompt, responseType, new Object[0]);
    }

    // 기본 호출 (Tool 포함), 응답 파싱 실패 시 1회 재시도
    public <T> T call(
            AiPrompt prompt,
            Class<T> responseType,
            Object... tools) {

        try {
            return callEntity(prompt, responseType, tools);
        } catch (RuntimeException e) {
            log.warn(
                    "AI 응답 파싱에 실패하여 1회 재시도합니다. 원인: {}",
                    e.toString()
            );

            return callEntity(prompt, responseType, tools);
        }
    }

    private <T> T callEntity(
            AiPrompt prompt,
            Class<T> responseType,
            Object... tools) {

        return chatClient
                .prompt()
                .system(prompt.system())
                .user(prompt.user())
                .tools(tools)
                .call()
                .entity(responseType);
    }

    // 커스텀 OutputConverter 호출 (Tool 포함), 파싱 실패 시 1회 재시도
    // 결과 유효성 검증이 필요 없는 호출부는 항상 통과하는 검증을 적용
    public <T> T call(
            AiPrompt prompt,
            BeanOutputConverter<T> outputConverter,
            Object... tools) {

        return call(prompt, outputConverter, result -> true, tools);
    }

    // 커스텀 OutputConverter 호출 (Tool 포함) + 결과 유효성 검증, 실패 시 1회 재시도
    // 파싱 예외뿐 아니라 파싱은 성공했지만 isValid를 통과하지 못한 빈 응답도 재시도 대상으로 취급
    public <T> T call(
            AiPrompt prompt,
            BeanOutputConverter<T> outputConverter,
            Predicate<T> isValid,
            Object... tools) {

        try {
            return callAndConvertValid(prompt, outputConverter, isValid, tools);
        } catch (RuntimeException e) {
            log.warn(
                    "AI 구조화 응답 파싱 또는 검증에 실패하여 1회 재시도합니다. 원인: {}",
                    e.toString()
            );

            return callAndConvertValid(prompt, outputConverter, isValid, tools);
        }
    }

    private <T> T callAndConvertValid(
            AiPrompt prompt,
            BeanOutputConverter<T> outputConverter,
            Predicate<T> isValid,
            Object... tools) {

        for (Object tool : tools) {
            if (tool instanceof com.planb.ai.mcp.PlanTourismTool planTool) {
                planTool.resetCandidates();
            }
        }
        String content = fetchContent(prompt, outputConverter, tools);
        T result = convert(outputConverter, content);

        // TODO(diagnostic): planDays가 왜 계속 null로 오는지 원인 조사용 임시 로그.
        // 원인 파악 끝나면 이 로그(및 이 주석)는 지워야 함.
        if (!isValid.test(result)) {
            log.warn(
                    "AI 구조화 응답이 파싱은 성공했지만 내용이 비어 있습니다. 원본 응답: {}",
                    content
            );

            throw new IllegalStateException(
                    "AI 구조화 응답이 파싱은 성공했지만 내용이 비어 있습니다: " + result
            );
        }

        return result;
    }

    // OpenAI Structured Outputs(response_format=json_schema, strict) 강제 적용
    // BeanOutputConverter가 만든 스키마에 정규화 보정을 거쳐 사용, 모델이 스키마를 벗어난
    // 토큰(괄호 누락·중복 등 문법 오류 포함)을 아예 생성하지 못하도록 API 레벨에서 강제
    private <T> String fetchContent(
            AiPrompt prompt,
            BeanOutputConverter<T> outputConverter,
            Object... tools) {

        String schema = normalizeSchema(outputConverter.getJsonSchema());

        return chatClient
                .prompt()
                .system(prompt.system())
                .user(prompt.user())
                .tools(tools)
                .options(
                        OpenAiChatOptions.builder()
                                .responseFormat(
                                        OpenAiChatModel.ResponseFormat.builder()
                                                .type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
                                                .jsonSchema(schema)
                                                .build()
                                )
                )
                .call()
                .content();
    }

    // NULLABLE_SCHEDULE_FIELDS null 허용 처리 + date/time format 제거된 스키마 문자열 반환
    // required는 유지하되 타입 유니언으로 null을 허용해, strict 모드에서도
    // 값이 없어야 하는 슬롯에 AI가 억지로 값을 채우지 않도록 함
    private String normalizeSchema(String schemaJson) {

        JsonNode root = SCHEMA_MAPPER.readTree(schemaJson);
        normalizeNode(root);

        return root.toString();
    }

    private void normalizeNode(JsonNode node) {

        if (node == null || !node.isObject()) {
            return;
        }

        removeUnsafeFormat((ObjectNode) node);

        JsonNode properties = node.get("properties");
        if (properties != null && properties.isObject()) {
            properties.properties().forEach(entry -> {
                if (NULLABLE_SCHEDULE_FIELDS.contains(entry.getKey())
                        && entry.getValue().isObject()) {

                    addNullType((ObjectNode) entry.getValue());
                }
            });
        }

        node.forEach(this::normalizeNode);
    }

    // format:"date"/"time" 제거, RFC3339 해석(타임존·밀리초 포함) 방지
    // 애플리케이션은 항상 자체 lenient 파서로 시간·날짜를 다루므로
    // 모델이 표준 포맷을 강제로 따르게 둘 필요가 없음
    private void removeUnsafeFormat(ObjectNode node) {

        JsonNode format = node.get("format");
        if (format != null && STRICT_UNSAFE_FORMATS.contains(format.asText())) {
            node.remove("format");
        }
    }

    private void addNullType(ObjectNode fieldSchema) {

        JsonNode type = fieldSchema.get("type");
        ArrayNode nullableType = SCHEMA_MAPPER.createArrayNode();
        boolean alreadyNullable = false;

        if (type != null && type.isArray()) {
            for (JsonNode t : type) {
                nullableType.add(t);
                if ("null".equals(t.asText())) {
                    alreadyNullable = true;
                }
            }
        } else if (type != null) {
            nullableType.add(type);
        }

        if (!alreadyNullable) {
            nullableType.add("null");
        }

        fieldSchema.set("type", nullableType);
    }

    private <T> T convert(
            BeanOutputConverter<T> outputConverter,
            String content) {

        try {
            return outputConverter.convert(content);
        } catch (RuntimeException e) {
            log.warn(
                    "AI 구조화 응답 JSON 파싱 실패. 원본 응답: {}",
                    content
            );

            throw e;
        }
    }

    // 스트리밍 호출
    public Flux<String> stream(AiPrompt prompt) {

        return chatClient
                .prompt()
                .system(prompt.system())
                .user(prompt.user())
                .stream()
                .content();
    }
}
