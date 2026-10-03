package com.planb.performance.openai;

import com.planb.ai.client.OpenAiClient;
import com.planb.ai.prompt.AiPrompt;
import com.planb.global.config.exception.AiFailure;
import com.planb.global.config.exception.domain.AiOrchestrationException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.tool.annotation.Tool;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiProtocolStubTest {

    private static final JsonMapper JSON_MAPPER = JsonMapper
            .builder()
            .build();

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final AiPrompt prompt = new AiPrompt() {
        @Override
        public String system() {
            return "system-prompt";
        }

        @Override
        public String user() {
            return "user-prompt";
        }
    };

    private OpenAiChatCompletionStub stub;

    private OpenAiClient openAiClient;

    private BeanOutputConverter<StubResponse> outputConverter;

    @BeforeEach
    void setUp() {

        stub = new OpenAiChatCompletionStub();
        openAiClient = openAiClient(
                stub,
                Duration.ofSeconds(2)
        );

        outputConverter = new BeanOutputConverter<>(StubResponse.class);
    }

    @AfterEach
    void tearDown() {

        stub.close();
    }

    @Test
    @DisplayName("로컬 OpenAI 스텁 기반 실제 Tool 호출 왕복")
    void toolCallingRoundTrip() {

        stub.enqueueFixtures(
                "tool-call.json",
                "success.json"
        );

        StubTool tool = new StubTool();

        StubResponse response = openAiClient.call(
                prompt,
                outputConverter,
                tool
        );

        assertEquals(new StubResponse("success"), response);
        assertEquals(1, tool.callCount());
        assertEquals(
                2,
                stub
                        .requests()
                        .size()
        );

        JsonNode initialRequest = request(0);
        assertEquals(
                "planb-stub",
                initialRequest
                        .get("model")
                        .asText()
        );
        assertEquals(
                "json_schema",
                initialRequest
                        .get("response_format")
                        .get("type")
                        .asText()
        );
        assertTrue(
                initialRequest
                        .get("response_format")
                        .get("json_schema")
                        .get("strict")
                        .asBoolean()
        );
        assertTrue(
                initialRequest
                        .get("tools")
                        .toString()
                        .contains("lookupValue")
        );

        JsonNode followUpRequest = request(1);
        assertTrue(
                followUpRequest
                        .get("messages")
                        .toString()
                        .contains("call_lookup_1")
        );
        assertTrue(
                followUpRequest
                        .get("messages")
                        .toString()
                        .contains("tool-value")
        );
    }

    @Test
    @DisplayName("파싱 실패 후 동일 OpenAI 경계 재시도 성공")
    void parsingFailureThenSuccess() {

        stub.enqueueFixtures(
                "parse-failure.json",
                "success.json"
        );

        StubResponse response = openAiClient.call(
                prompt,
                outputConverter
        );

        assertEquals(new StubResponse("success"), response);
        assertEquals(
                2,
                stub
                        .requests()
                        .size()
        );
    }

    @Test
    @DisplayName("검증 실패 사유와 이전 응답을 포함한 교정 성공")
    void validationFailureThenCorrectionSuccess() {

        stub.enqueueFixtures(
                "validation-failure.json",
                "success.json"
        );

        Function<StubResponse, List<String>> validation = this::validate;

        StubResponse response = openAiClient.call(
                prompt,
                outputConverter,
                validation
        );

        assertEquals(new StubResponse("success"), response);
        assertEquals(
                2,
                stub
                        .requests()
                        .size()
        );

        String correctionRequest = stub
                .requests()
                .get(1);
        assertTrue(correctionRequest.contains("Java 검증 교정 요청"));
        assertTrue(correctionRequest.contains("value는 success여야 합니다."));
        assertTrue(correctionRequest.contains("invalid"));
    }

    @Test
    @DisplayName("동일한 무효 응답 반복 차단")
    void repeatedInvalidResponse() {

        stub.enqueueFixtures(
                "validation-failure.json",
                "validation-failure.json"
        );

        Function<StubResponse, List<String>> validation = this::validate;

        AiOrchestrationException exception = assertThrows(
                AiOrchestrationException.class,
                () -> openAiClient.call(
                        prompt,
                        outputConverter,
                        validation
                )
        );

        assertEquals(AiFailure.RESPONSE_REPEATED_INVALID, exception.getFailure());
        assertEquals(
                2,
                stub
                        .requests()
                        .size()
        );
    }

    @Test
    @DisplayName("동시 요청별 Tool 상태 기반 응답 선택")
    void responseSelectionByToolStateUnderConcurrency() throws Exception {

        LocalDate startDate = LocalDate.of(
                2030,
                1,
                1
        );

        try (OpenAiChatCompletionStub loadTestStub =
                     OpenAiChatCompletionStub.startTravelPlan(
                             0,
                             startDate,
                             startDate.plusDays(1)
                     );
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            List<Callable<HttpResponse<String>>> calls = IntStream
                    .range(
                            0,
                            20
                    )
                    .mapToObj(index -> (Callable<HttpResponse<String>>) () -> request(
                            loadTestStub,
                            index % 2 == 0
                                    ? "{\"messages\":[{\"role\":\"user\",\"content\":\"plan\"}]}"
                                    : "{\"messages\":[{\"role\":\"tool\",\"tool_call_id\":\"call_attractions_1\",\"content\":\"[]\"}]}"
                    ))
                    .toList();

            List<Future<HttpResponse<String>>> responses = executor.invokeAll(calls);

            for (int index = 0; index < responses.size(); index++) {
                HttpResponse<String> response = responses
                        .get(index)
                        .get();

                assertEquals(
                        200,
                        response.statusCode()
                );

                JsonNode message = JSON_MAPPER
                        .readTree(response.body())
                        .get("choices")
                        .get(0)
                        .get("message");

                if (index % 2 == 0) {
                    assertEquals(
                            "searchAttractionsByRegion",
                            message
                                    .get("tool_calls")
                                    .get(0)
                                    .get("function")
                                    .get("name")
                                    .asText()
                    );
                } else {
                    assertTrue(message
                            .get("content")
                            .asText()
                            .contains(startDate.toString()));
                }
            }
        }
    }

    @Test
    @DisplayName("일정 생성 OpenAI 스텁의 고정 응답 지연")
    void fixedResponseDelay() throws Exception {

        LocalDate startDate = LocalDate.of(
                2030,
                1,
                1
        );

        try (OpenAiChatCompletionStub loadTestStub =
                     OpenAiChatCompletionStub.startTravelPlan(
                             0,
                             startDate,
                             startDate.plusDays(1),
                             Duration.ofMillis(100)
                     )) {

            long startedAt = System.nanoTime();

            HttpResponse<String> response = request(
                    loadTestStub,
                    "{\"messages\":[{\"role\":\"user\",\"content\":\"plan\"}]}"
            );

            long elapsedMillis = Duration
                    .ofNanos(System.nanoTime() - startedAt)
                    .toMillis();

            assertEquals(200, response.statusCode());
            assertTrue(
                    elapsedMillis >= 80,
                    "고정 지연 100ms보다 너무 빨리 응답함: " + elapsedMillis + "ms"
            );
        }
    }

    @Test
    @DisplayName("음수 OpenAI 스텁 응답 지연 거부")
    void negativeResponseDelay() {

        LocalDate startDate = LocalDate.of(
                2030,
                1,
                1
        );

        assertThrows(
                IllegalArgumentException.class,
                () -> OpenAiChatCompletionStub.startTravelPlan(
                        0,
                        startDate,
                        startDate.plusDays(1),
                        Duration.ofMillis(-1)
                )
        );
    }

    @Test
    @DisplayName("고정 지연 중 동시 OpenAI 스텁 응답")
    void concurrentFixedResponseDelay() throws Exception {

        LocalDate startDate = LocalDate.of(
                2030,
                1,
                1
        );

        try (OpenAiChatCompletionStub loadTestStub =
                     OpenAiChatCompletionStub.startTravelPlan(
                             0,
                             startDate,
                             startDate.plusDays(1),
                             Duration.ofMillis(300)
                     );
             ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {

            List<Callable<HttpResponse<String>>> calls = IntStream
                    .range(
                            0,
                            6
                    )
                    .mapToObj(index -> (Callable<HttpResponse<String>>) () -> request(
                            loadTestStub,
                            "{\"messages\":[{\"role\":\"user\",\"content\":\"plan\"}]}"
                    ))
                    .toList();

            List<Future<HttpResponse<String>>> responses = executor.invokeAll(calls);

            for (Future<HttpResponse<String>> response : responses) {
                assertEquals(
                        200,
                        response
                                .get()
                                .statusCode()
                );
            }

            assertTrue(
                    loadTestStub.maxConcurrentRequests() > 1,
                    "고정 지연 응답이 겹쳐 처리되지 않음"
            );
        }
    }

    @Test
    @DisplayName("고정 지연 OpenAI Tool 왕복 2회")
    void delayedToolCallingRoundTrip() {

        try (OpenAiChatCompletionStub delayedStub =
                     new OpenAiChatCompletionStub(Duration.ofMillis(100))) {

            delayedStub.enqueueFixtures(
                    "tool-call.json",
                    "success.json"
            );

            StubTool tool = new StubTool();
            long startedAt = System.nanoTime();

            StubResponse response = openAiClient(
                    delayedStub,
                    Duration.ofSeconds(1)
            )
                    .call(
                    prompt,
                    outputConverter,
                    tool
            );

            long elapsedMillis = Duration
                    .ofNanos(System.nanoTime() - startedAt)
                    .toMillis();

            assertEquals(new StubResponse("success"), response);
            assertEquals(1, tool.callCount());
            assertEquals(
                    2,
                    delayedStub
                            .requests()
                            .size()
            );
            assertTrue(elapsedMillis >= 180);
        }
    }

    private OpenAiClient openAiClient(
            OpenAiChatCompletionStub target,
            Duration timeout
    ) {

        OpenAiChatOptions options = OpenAiChatOptions
                .builder()
                .baseUrl(target.baseUrl())
                .apiKey("planb-stub")
                .model("planb-stub")
                .timeout(timeout)
                .maxRetries(0)
                .build();

        OpenAiChatModel model = OpenAiChatModel
                .builder()
                .options(options)
                .build();

        return new OpenAiClient(
                ChatClient.create(model),
                new SimpleMeterRegistry()
        );
    }

    private List<String> validate(StubResponse response) {

        return "success".equals(response.value())
                ? List.of()
                : List.of("value는 success여야 합니다.");
    }

    private JsonNode request(int index) {

        return JSON_MAPPER.readTree(
                stub
                        .requests()
                        .get(index)
        );
    }

    private HttpResponse<String> request(
            OpenAiChatCompletionStub target,
            String body
    ) throws Exception {

        HttpRequest request = HttpRequest
                .newBuilder(URI.create(target.baseUrl() + "/chat/completions"))
                .header(
                        "Content-Type",
                        "application/json"
                )
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return HTTP_CLIENT.send(
                request,
                HttpResponse.BodyHandlers.ofString()
        );
    }

    record StubResponse(String value) { }

    static class StubTool {

        private int callCount;

        @Tool(description = "키에 해당하는 테스트 값을 조회")
        public String lookupValue(String key) {

            callCount++;

            return "tool-value";
        }

        int callCount() {

            return callCount;
        }
    }
}
