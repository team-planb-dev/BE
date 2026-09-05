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
import java.util.function.Predicate;

/**
 * STOMP 기반 WebSocket 통합 테스트 지원 클래스.
 * STOMP 클라이언트 생성, 연결, 구독 및
 * 특정 메시지 타입 대기 기능 제공
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

    // STOMP 클라이언트 생성
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

    // WebSocket 연결 및 STOMP 세션 생성
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

    // 채팅방 구독 및 수신 메시지 큐 등록
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

    // 지정 타입 메시지 수신 대기
    public SendChatMessageResponse awaitMessageType(
            BlockingQueue<SendChatMessageResponse> messages,
            MessageType expectedType
    ) throws InterruptedException {

        return awaitMessage(
                messages,
                message -> message.type() == expectedType
        );
    }

    // 조건(Predicate) 기반 메시지 수신 대기
    // TALK 타입은 본인 echo와 AI 봇 응답이 모두 같은 타입으로 오므로,
    // 타입만으로 구분이 안 되는 경우 이 메소드로 발신자 등 추가 조건 지정
    public SendChatMessageResponse awaitMessage(
            BlockingQueue<SendChatMessageResponse> messages,
            Predicate<SendChatMessageResponse> condition
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

            if (condition.test(response)) {
                return response;
            }
        }

        return null;
    }

    // 구독 시 발생하는 ENTER 메시지 소진
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

    // STOMP 세션 연결 종료
    public void disconnect(
            StompSession session
    ) {

        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }

    // STOMP 클라이언트 종료
    public void stop(
            WebSocketStompClient stompClient
    ) {

        if (stompClient != null) {
            stompClient.stop();
        }
    }
}
