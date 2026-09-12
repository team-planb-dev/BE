package com.planb.unit.global.ai.client;

import com.planb.ai.client.OpenAiClient;
import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.mcp.PlanTourismTool;
import com.planb.ai.mcp.TourismTool;
import com.planb.ai.prompt.AiPrompt;
import com.planb.global.config.exception.AiFailure;
import com.planb.global.config.exception.domain.AiOrchestrationException;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class OpenAiClientTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private BeanOutputConverter<TestDto> outputConverter;

    @InjectMocks
    private OpenAiClient openAiClient;

    private final AiPrompt prompt =
            new AiPrompt() {
                @Override
                public String system() {
                    return "system-prompt";
                }

                @Override
                public String user() {
                    return "user-prompt";
                }
            };

    private record TestDto(String value) {
    }

    @BeforeEach
    void schemaForMockConverter() {
        lenient().when(outputConverter.getJsonSchema()).thenReturn("{} ");
    }

    @Test
    @DisplayName("JSON 파싱 재시도에도 실패한 호출의 검색 후보 유지")
    void parsingRetryKeepsCandidatesFromFailedAttempt() {
        PlaceCandidateContext candidates = new PlaceCandidateContext();
        PlanTourismTool tool = new PlanTourismTool(mock(TourismTool.class), candidates);
        when(chatClient.prompt().system(prompt.system()).user(prompt.user()).tools(tool)
                .options(any()).call().content()).thenReturn("raw");
        when(outputConverter.convert("raw")).thenAnswer(invocation -> {
            candidates.record(new PlaceWithRouteResult(true, "카페", "부산", "129.1", "35.1", null,
                    "kakao:first", "CE7", "카페"));
            throw new IllegalArgumentException("잘못된 JSON");
        }).thenAnswer(invocation -> {
            // 후보는 외부 검색으로 확인한 사실이므로 응답 파싱 실패와 무관하게 남는다
            assertNotNull(candidates.find("kakao:first"));
            candidates.record(new PlaceWithRouteResult(true, "두 번째 카페", "부산", "129.1", "35.1", null,
                    "kakao:second", "CE7", "카페"));
            return new TestDto("ok");
        });
        assertEquals(new TestDto("ok"), openAiClient.call(prompt, outputConverter, tool));
        assertNotNull(candidates.find("kakao:first"));
        assertNotNull(candidates.find("kakao:second"));
        verify(outputConverter, times(2)).convert("raw");
    }

    @Test
    @DisplayName("파싱 1차 실패 시 1회 재시도 후 성공")
    void call_withClassResponseType_retriesOnceThenSucceeds() {


        TestDto expected = new TestDto("ok");

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(prompt.user())
                        .tools()
                        .call()
                        .entity(TestDto.class)
        ).thenThrow(
                new RuntimeException("1차 파싱 실패")
        ).thenReturn(
                expected
        );

        TestDto result = openAiClient.call(prompt, TestDto.class);

        assertEquals(expected, result);

        verify(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(prompt.user())
                        .tools()
                        .call(),
                times(2)
        ).entity(TestDto.class);
    }

    @Test
    @DisplayName("파싱 2회 연속 실패의 AI 호출 실패 분류")
    void call_withClassResponseType_throwsWhenBothAttemptsFail() {

        // 분류되지 않은 SDK 예외가 그대로 올라가면 BASE.EXCEPTION.EXCEPTION_ISSUED로 나간다.
        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(prompt.user())
                        .tools()
                        .call()
                        .entity(TestDto.class)
        ).thenThrow(
                new RuntimeException("1차 파싱 실패")
        ).thenThrow(
                new RuntimeException("2차 파싱 실패")
        );

        AiOrchestrationException exception = assertThrows(
                AiOrchestrationException.class,
                () -> openAiClient.call(prompt, TestDto.class)
        );

        assertEquals(
                AiFailure.UPSTREAM_CALL_FAILED,
                exception.getFailure()
        );
    }

    @Test
    @DisplayName("correction 재시도 시 이전 응답이 선택한 검색 후보 유지")
    void correctionRetryKeepsCandidatesFromPreviousAttempt() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        PlanTourismTool tool = new PlanTourismTool(mock(TourismTool.class), candidates);

        TestDto invalid = new TestDto(null);

        TestDto valid = new TestDto("ok");

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(prompt.user())
                        .tools(tool)
                        .options(any())
                        .call()
                        .content()
        ).thenReturn("raw-1");

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(contains("이전 실패 응답:\nraw-1"))
                        .tools(tool)
                        .options(any())
                        .call()
                        .content()
        ).thenReturn("raw-2");

        when(
                outputConverter.convert("raw-1")
        ).thenAnswer(invocation -> {
            candidates.record(new PlaceWithRouteResult(
                    true,
                    "카페",
                    "부산",
                    "129.1",
                    "35.1",
                    null,
                    "kakao:first",
                    "CE7",
                    "카페"));

            return invalid;
        });

        // correction 응답은 이전 응답을 고친 것이므로 그 응답이 가리키던 후보가 남아 있어야 한다
        when(
                outputConverter.convert("raw-2")
        ).thenAnswer(invocation -> {
            assertNotNull(candidates.find("kakao:first"));

            return valid;
        });

        Function<TestDto, List<String>> validation = value -> value.value() == null
                ? List.of("value 누락")
                : List.of();

        TestDto result = openAiClient.call(
                prompt,
                outputConverter,
                validation,
                tool
        );

        assertEquals(valid, result);
        assertNotNull(candidates.find("kakao:first"));
    }

    @Test
    @DisplayName("검증 실패 사유를 correction 요청에 포함하고 1회 재시도")
    void call_withValidationReason_retriesWithCorrection() {


        TestDto invalid = new TestDto(null);
        TestDto valid = new TestDto("ok");
        String reason = "day1 관광지 3개 필요 / 실제 1개";
        String missingChanges = "changes 필드에 실제 수정 내역 필요";

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(prompt.user())
                        .tools()
                        .options(any())
                        .call()
                        .content()
        ).thenReturn("raw-1");

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(contains(
                                "누락 또는 위반 조건:\n- " + reason
                                        + "\n- " + missingChanges
                                        + "\n이전 실패 응답:\nraw-1"
                        ))
                        .tools()
                        .options(any())
                        .call()
                        .content()
        ).thenReturn("raw-2");

        when(
                outputConverter.convert("raw-1")
        ).thenReturn(
                invalid
        );

        when(
                outputConverter.convert("raw-2")
        ).thenReturn(
                valid
        );

        Function<TestDto, List<String>> validation = dto -> dto.value() == null
                ? List.of(
                        reason,
                        missingChanges
                )
                : List.of();

        TestDto result = openAiClient.call(
                prompt,
                outputConverter,
                validation
        );

        assertEquals(valid, result);

        verify(
                outputConverter,
                times(2)
        ).convert(any());
    }

    @Test
    @DisplayName("동일한 무효 구조화 응답 반복 차단")
    void call_withRepeatedInvalidResponse_throwsRepeatedResponseFailure() {

        TestDto invalid = new TestDto(null);
        String reason = "changes 필드에 실제 수정 내역 필요";

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(anyString())
                        .tools()
                        .options(any())
                        .call()
                        .content()
        ).thenReturn(
                "raw-1",
                "raw-1"
        );

        when(
                outputConverter.convert("raw-1")
        ).thenReturn(
                invalid
        );

        Function<TestDto, List<String>> validation = dto -> List.of(reason);

        AiOrchestrationException exception = assertThrows(
                AiOrchestrationException.class,
                () -> openAiClient.call(
                        prompt,
                        outputConverter,
                        validation
                )
        );

        assertEquals(
                AiFailure.RESPONSE_REPEATED_INVALID,
                exception.getFailure()
        );

        assertFalse(exception.getFailure().isRetryable());
    }

    @Test
    @DisplayName("2회 연속 빈 응답의 재시도 가능 분류")
    void call_withRepeatedEmptyResponse_classifiesAsRetryableFailure() {

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(anyString())
                        .tools()
                        .options(any())
                        .call()
                        .content()
        ).thenReturn(
                " ",
                " "
        );

        Function<TestDto, List<String>> validation = dto -> List.of();

        AiOrchestrationException exception = assertThrows(
                AiOrchestrationException.class,
                () -> openAiClient.call(
                        prompt,
                        outputConverter,
                        validation
                )
        );

        assertEquals(
                AiFailure.RESPONSE_EMPTY,
                exception.getFailure()
        );
    }

    @Test
    @DisplayName("correction 응답도 검증 실패하면 기존 재시도 한도에서 종료")
    void call_withValidationReason_throwsWhenCorrectionIsInvalid() {


        TestDto invalid = new TestDto(null);
        String reason = "day1 관광지 3개 필요 / 실제 1개";

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(anyString())
                        .tools()
                        .options(any())
                        .call()
                        .content()
        ).thenReturn(
                "raw-1",
                "raw-2"
        );

        when(
                outputConverter.convert(any())
        ).thenReturn(
                invalid
        );

        Function<TestDto, List<String>> validation = dto -> List.of(reason);

        AiOrchestrationException exception = assertThrows(
                AiOrchestrationException.class,
                () -> openAiClient.call(
                        prompt,
                        outputConverter,
                        validation
                )
        );

        assertEquals(
                AiFailure.RESPONSE_INVALID,
                exception.getFailure()
        );

        assertTrue(exception.getMessage().contains(reason));
        verify(
                outputConverter,
                times(2)
        ).convert(any());
    }

    @Test
    @DisplayName("실제 빈 구조화 응답은 validation correction과 구분하여 재시도")
    void call_withEmptyResponse_retriesWithoutCorrection() {

        TestDto valid = new TestDto("ok");

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(prompt.user())
                        .tools()
                        .options(any())
                        .call()
                        .content()
        ).thenReturn(
                " ",
                "raw-2"
        );

        when(outputConverter.convert("raw-2"))
                .thenReturn(valid);

        Function<TestDto, List<String>> validation = dto -> List.of();

        assertEquals(
                valid,
                openAiClient.call(
                        prompt,
                        outputConverter,
                        validation
                )
        );

        verify(
                outputConverter,
                times(1)
        ).convert(any());
    }

    @Test
    @DisplayName("isValid 미지정 시 항상 통과, 1회만 호출")
    void call_withoutIsValid_defaultsToAlwaysValid() {


        TestDto result = new TestDto(null);

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(prompt.user())
                        .tools()
                        .options(any())
                        .call()
                        .content()
        ).thenReturn(
                "raw"
        );

        when(
                outputConverter.convert("raw")
        ).thenReturn(
                result
        );

        TestDto actual = openAiClient.call(prompt, outputConverter);

        assertEquals(result, actual);

        verify(
                outputConverter,
                times(1)
        ).convert(any());
    }

    @Test
    @DisplayName("스트리밍 호출 시 content Flux 반환")
    void stream_returnsContentFlux() {


        Flux<String> expected = Flux.just("a", "b");

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(prompt.user())
                        .stream()
                        .content()
        ).thenReturn(
                expected
        );

        Flux<String> result = openAiClient.stream(prompt);

        StepVerifier.create(result)
                .expectNext("a", "b")
                .verifyComplete();
    }
}
