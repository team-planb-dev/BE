package com.planb.integration.domain.chat.helper;

import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.response.SendChatMessageResponse;

import java.lang.reflect.Type;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * STOMP 기반 WebSocket 통합 테스트 지원 클래스.
 * STOMP 클라이언트 생성, 연결, 구독 및
 * 특정 메시지 타입을 기다리는 기능을 제공한다.
 */
public class StompTestClientHelper {

    private static final String CHAT_SUBSCRIPTION_PREFIX =
            "/sub/api/v1/chat/";

    private static final int CONNECTION_TIMEOUT_SECONDS =
            5;

    private static final int MESSAGE_TIMEOUT_SECONDS =
            5;

    private final int port;

    public StompTestClientHelper(
            int port
    ) {
        this.port = port;
    }

    public WebSocketStompClient createStompClient() {

        WebSocketStompClient stompClient =
                new WebSocketStompClient(
                        new StandardWebSocketClient()
                );

        stompClient.setMessageConverter(
                new JacksonJsonMessageConverter()
        );

        return stompClient;
    }

    public StompSession connect(
            WebSocketStompClient stompClient,
            String accessToken
    ) throws Exception {

        String webSocketUrl =
                "ws://localhost:"
                        + port
                        + "/ws-stomp";

        StompHeaders connectHeaders =
                new StompHeaders();

        connectHeaders.add(
                "Authorization",
                accessToken
        );

        return stompClient
                .connectAsync(
                        webSocketUrl,
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                        }
                )
                .get(
                        CONNECTION_TIMEOUT_SECONDS,
                        TimeUnit.SECONDS
                );
    }

    public void subscribe(
            StompSession session,
            Long roomId,
            BlockingQueue<SendChatMessageResponse> messages
    ) {

        session.subscribe(
                CHAT_SUBSCRIPTION_PREFIX + roomId,
                new StompFrameHandler() {

                    @Override
                    public Type getPayloadType(
                            StompHeaders headers
                    ) {
                        return SendChatMessageResponse.class;
                    }

                    @Override
                    public void handleFrame(
                            StompHeaders headers,
                            Object payload
                    ) {

                        messages.offer(
                                (SendChatMessageResponse) payload
                        );
                    }
                }
        );
    }

    public SendChatMessageResponse awaitMessageType(
            BlockingQueue<SendChatMessageResponse> messages,
            MessageType expectedType
    ) throws InterruptedException {

        long timeoutAt =
                System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(
                        MESSAGE_TIMEOUT_SECONDS
                );

        while (System.nanoTime() < timeoutAt) {

            SendChatMessageResponse response =
                    messages.poll(
                            500,
                            TimeUnit.MILLISECONDS
                    );

            if (response == null) {
                continue;
            }

            if (response.type() == expectedType) {
                return response;
            }
        }

        return null;
    }

    public void drainPresenceMessages(
            BlockingQueue<SendChatMessageResponse> messages
    ) throws InterruptedException {

        long timeoutAt =
                System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(1);

        while (System.nanoTime() < timeoutAt) {

            SendChatMessageResponse response =
                    messages.poll(
                            100,
                            TimeUnit.MILLISECONDS
                    );

            if (response == null) {
                continue;
            }

            if (response.type() != MessageType.ENTER) {
                messages.offer(response);
                return;
            }
        }
    }

    public void disconnect(
            StompSession session
    ) {

        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    public void stop(
            WebSocketStompClient stompClient
    ) {

        if (stompClient != null) {
            stompClient.stop();
        }
    }
}