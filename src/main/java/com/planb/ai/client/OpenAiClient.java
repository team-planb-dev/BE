package com.planb.ai.client;

import com.planb.ai.prompt.AiPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.function.Predicate;

@Slf4j
@Component
@RequiredArgsConstructor
public class OpenAiClient {

    private final ChatClient chatClient;

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
            StructuredOutputConverter<T> outputConverter,
            Object... tools) {

        return call(prompt, outputConverter, result -> true, tools);
    }

    // 커스텀 OutputConverter 호출 (Tool 포함) + 결과 유효성 검증, 실패 시 1회 재시도
    // 파싱 예외뿐 아니라 파싱은 성공했지만 isValid를 통과하지 못한 빈 응답도 재시도 대상으로 취급
    public <T> T call(
            AiPrompt prompt,
            StructuredOutputConverter<T> outputConverter,
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
            StructuredOutputConverter<T> outputConverter,
            Predicate<T> isValid,
            Object... tools) {

        String content = fetchContent(prompt, tools);
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

    private String fetchContent(
            AiPrompt prompt,
            Object... tools) {

        return chatClient
                .prompt()
                .system(prompt.system())
                .user(prompt.user())
                .tools(tools)
                .call()
                .content();
    }

    private <T> T convert(
            StructuredOutputConverter<T> outputConverter,
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
