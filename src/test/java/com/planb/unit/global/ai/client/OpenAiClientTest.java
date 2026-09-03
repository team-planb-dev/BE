package com.planb.unit.global.ai.client;

import com.planb.ai.client.OpenAiClient;
import com.planb.ai.prompt.AiPrompt;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.StructuredOutputConverter;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;


@ExtendWith(MockitoExtension.class)
class OpenAiClientTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private ChatClient chatClient;

    @Mock
    private StructuredOutputConverter<TestDto> outputConverter;

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
    @DisplayName("파싱 2회 연속 실패 시 예외 전파")
    void call_withClassResponseType_throwsWhenBothAttemptsFail() {


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

        assertThrows(
                RuntimeException.class,
                () -> openAiClient.call(prompt, TestDto.class)
        );
    }

    @Test
    @DisplayName("isValid 1차 실패 시 1회 재시도 후 유효한 결과 반환")
    void call_withIsValid_retriesOnceWhenFirstResultInvalid() {


        TestDto invalid = new TestDto(null);
        TestDto valid = new TestDto("ok");

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(prompt.user())
                        .tools()
                        .call()
                        .content()
        ).thenReturn(
                "raw-1",
                "raw-2"
        );

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

        Predicate<TestDto> isValid = dto -> dto.value() != null;

        TestDto result = openAiClient.call(prompt, outputConverter, isValid);

        assertEquals(valid, result);

        verify(
                outputConverter,
                times(2)
        ).convert(any());
    }

    @Test
    @DisplayName("isValid 2회 연속 실패 시 예외 발생")
    void call_withIsValid_throwsWhenBothAttemptsInvalid() {


        TestDto invalid = new TestDto(null);

        when(
                chatClient.prompt()
                        .system(prompt.system())
                        .user(prompt.user())
                        .tools()
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

        Predicate<TestDto> isValid = dto -> dto.value() != null;

        assertThrows(
                IllegalStateException.class,
                () -> openAiClient.call(prompt, outputConverter, isValid)
        );
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
