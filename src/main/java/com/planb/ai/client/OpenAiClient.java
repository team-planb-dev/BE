package com.planb.ai.client;

import com.planb.ai.prompt.AiPrompt;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Component
@RequiredArgsConstructor
public class OpenAiClient {

    private final ChatClient chatClient;

    // 기본 호출
    public <T> T call
            (AiPrompt prompt,
             Class<T> responseType ){

        return chatClient
                .prompt()
                .system(prompt
                        .system())
                .user(prompt
                        .user())
                .call()
                .entity(responseType);
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
