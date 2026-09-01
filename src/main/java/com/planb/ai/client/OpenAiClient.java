package com.planb.ai.client;

import com.planb.ai.prompt.AiPrompt;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

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
    public <T> T call(
            AiPrompt prompt,
            StructuredOutputConverter<T> outputConverter,
            Object... tools) {

        try {
            return callAndConvert(
                    prompt,
                    outputConverter,
                    tools
            );
        } catch (RuntimeException e) {
            log.warn(
                    "AI 구조화 응답 파싱에 실패하여 1회 재시도합니다. 원인: {}",
                    e.toString()
            );

            return callAndConvert(
                    prompt,
                    outputConverter,
                    tools
            );
        }
    }

    private <T> T callAndConvert(
            AiPrompt prompt,
            StructuredOutputConverter<T> outputConverter,
            Object... tools) {

        String content = chatClient
                .prompt()
                .system(prompt.system())
                .user(prompt.user())
                .tools(tools)
                .call()
                .content();

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