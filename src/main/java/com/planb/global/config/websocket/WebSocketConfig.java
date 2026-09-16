package com.planb.global.config.websocket;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import com.planb.global.websocket.interceptor.StompChannelInterceptor;


@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompChannelInterceptor stompChannelInterceptor;

    @Override
    public void registerStompEndpoints
            (StompEndpointRegistry registry) {

        // 핸드셰이크 origin 검사는 MVC CORS 설정과 별개다.
        // 비워 두면 같은 origin만 통과해 로컬 프론트에서 403이 난다.
        registry
                .addEndpoint("/ws-stomp")
                .setAllowedOrigins(
                        "http://localhost:3000",
                        "http://localhost:5173"
                );
    }

    @Override
    public void configureMessageBroker
            (MessageBrokerRegistry registry) {

        registry
                .enableSimpleBroker("/sub");
        registry
                .setApplicationDestinationPrefixes("/pub");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompChannelInterceptor);
    }
}
