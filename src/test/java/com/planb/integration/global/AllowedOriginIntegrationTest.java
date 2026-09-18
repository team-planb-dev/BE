package com.planb.integration.global;

import com.planb.integration.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * 허용 origin은 HTTP CORS와 WebSocket 핸드셰이크가 따로 검사한다.
 * 두 목록이 어긋나 로컬 프론트의 STOMP 연결만 403이 난 적이 있어 양쪽을 함께 본다.
 */
class AllowedOriginIntegrationTest extends IntegrationTest {

    private static final String UNKNOWN_ORIGIN =
            "https://unknown.example.com";

    @LocalServerPort
    private int port;

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:3000",
            "http://localhost:5173",
            "https://yeoro-frontend.vercel.app"
    })
    @DisplayName("허용 origin은 HTTP preflight를 통과")
    void allowsPreflightFromKnownOrigin(String origin) throws Exception {

        mockMvc
                .perform(
                        options("/api/v1/travel/list")
                                .header("Origin", origin)
                                .header("Access-Control-Request-Method", "GET")
                )
                .andExpect(
                        header().string(
                                "Access-Control-Allow-Origin",
                                origin
                        )
                );
    }

    @Test
    @DisplayName("미등록 origin은 HTTP preflight에서 거부")
    void rejectsPreflightFromUnknownOrigin() throws Exception {

        mockMvc
                .perform(
                        options("/api/v1/travel/list")
                                .header("Origin", UNKNOWN_ORIGIN)
                                .header("Access-Control-Request-Method", "GET")
                )
                .andExpect(
                        header().doesNotExist(
                                "Access-Control-Allow-Origin"
                        )
                );
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://localhost:3000",
            "http://localhost:5173",
            "https://yeoro-frontend.vercel.app"
    })
    @DisplayName("허용 origin은 WebSocket 핸드셰이크를 통과")
    void allowsHandshakeFromKnownOrigin(String origin) throws Exception {

        WebSocketSession session =
                handshake(origin)
                        .get(
                                5,
                                TimeUnit.SECONDS
                        );

        try {
            assertThat(session.isOpen())
                    .isTrue();
        } finally {
            session.close();
        }
    }

    @Test
    @DisplayName("미등록 origin은 WebSocket 핸드셰이크에서 거부")
    void rejectsHandshakeFromUnknownOrigin() {

        assertThatThrownBy(() ->
                handshake(UNKNOWN_ORIGIN)
                        .get(
                                5,
                                TimeUnit.SECONDS
                        )
        ).isInstanceOf(ExecutionException.class);
    }

    private CompletableFuture<WebSocketSession> handshake(String origin) {

        WebSocketHttpHeaders headers =
                new WebSocketHttpHeaders();

        headers.setOrigin(origin);

        return new StandardWebSocketClient()
                .execute(
                        new AbstractWebSocketHandler() {
                        },
                        headers,
                        URI.create("ws://localhost:" + port + "/ws-stomp")
                );
    }
}
