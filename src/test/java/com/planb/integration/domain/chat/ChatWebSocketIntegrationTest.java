package com.planb.integration.domain.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.dto.response.SendChatMessageResponse;
import com.planb.integration.domain.chat.helper.ChatIntegrationTestSupport;
import com.planb.integration.domain.chat.helper.StompTestClientHelper;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chat WebSocket 및 STOMP 통합 테스트.
 * WebSocket 연결, 채팅방 구독,
 * 채팅 메시지 송수신과 입퇴장 시스템 메시지를 검증한다.
 */
public class ChatWebSocketIntegrationTest
        extends ChatIntegrationTestSupport {

    private static final String CHAT_SEND_PREFIX =
            "/pub/api/v1/chat/";

    @LocalServerPort
    private int port;

    private StompTestClientHelper stompHelper;

    @BeforeEach
    void setUpStompHelper() {

        stompHelper =
                new StompTestClientHelper(
                        port
                );
    }

    @Test
    @DisplayName("STOMP 연결 후 채팅 메시지 전송 및 수신 성공")
    void sendAndReceiveChatMessageSuccess() throws Exception {

        // given
        TestUser testUser =
                createAuthenticatedUser();

        Long roomId =
                createChatRoom(
                        testUser.accessToken(),
                        "stomp-message-room-"
                                + createUniqueValue()
                );

        addChatRoomMember(
                testUser.accessToken(),
                roomId,
                testUser.userId()
        );

        WebSocketStompClient stompClient =
                stompHelper.createStompClient();

        StompSession session = null;

        try {
            session =
                    stompHelper.connect(
                            stompClient,
                            testUser.accessToken()
                    );

            BlockingQueue<SendChatMessageResponse> messages =
                    new LinkedBlockingQueue<>();

            stompHelper.subscribe(
                    session,
                    roomId,
                    messages
            );

            /*
             * SUBSCRIBE 이벤트에서 ENTER 메시지가 먼저 올 수 있으므로
             * TALK 타입 메시지를 필터링해서 검증한다.
             */
            String messageContent =
                    "통합 테스트 메시지";

            // when
            session.send(
                    CHAT_SEND_PREFIX
                            + roomId
                            + "/send",
                    new SendChatMessageRequest(
                            messageContent
                    )
            );

            SendChatMessageResponse response =
                    stompHelper.awaitMessageType(
                            messages,
                            MessageType.TALK
                    );

            // then
            assertThat(response)
                    .isNotNull();

            assertThat(response.type())
                    .isEqualTo(MessageType.TALK);

            assertThat(response.roomId())
                    .isEqualTo(roomId);

            assertThat(response.senderId())
                    .isEqualTo(testUser.userId());

            assertThat(response.senderNickname())
                    .isEqualTo(testUser.nickname());

            assertThat(response.message())
                    .isEqualTo(messageContent);

            assertThat(response.sendTime())
                    .isNotNull();

        } finally {
            stompHelper.disconnect(session);
            stompHelper.stop(stompClient);
        }
    }

    @Test
    @DisplayName("STOMP 채팅방 구독 시 입장 시스템 메시지 자동 발행")
    void subscribePublishesEnterMessage() throws Exception {

        // given
        TestUser testUser =
                createAuthenticatedUser();

        Long roomId =
                createChatRoom(
                        testUser.accessToken(),
                        "subscribe-room-"
                                + createUniqueValue()
                );

        addChatRoomMember(
                testUser.accessToken(),
                roomId,
                testUser.userId()
        );

        WebSocketStompClient stompClient =
                stompHelper.createStompClient();

        StompSession session = null;

        try {
            session =
                    stompHelper.connect(
                            stompClient,
                            testUser.accessToken()
                    );

            BlockingQueue<SendChatMessageResponse> messages =
                    new LinkedBlockingQueue<>();

            // when
            stompHelper.subscribe(
                    session,
                    roomId,
                    messages
            );

            SendChatMessageResponse response =
                    stompHelper.awaitMessageType(
                            messages,
                            MessageType.ENTER
                    );

            // then
            assertThat(response)
                    .isNotNull();

            assertThat(response.type())
                    .isEqualTo(MessageType.ENTER);

            assertThat(response.roomId())
                    .isEqualTo(roomId);

            assertThat(response.senderId())
                    .isEqualTo(testUser.userId());

            assertThat(response.senderNickname())
                    .isEqualTo(testUser.nickname());

            assertThat(response.message())
                    .isEqualTo(
                            testUser.nickname()
                                    + "님이 입장했습니다."
                    );

            assertThat(response.sendTime())
                    .isNotNull();

        } finally {
            stompHelper.disconnect(session);
            stompHelper.stop(stompClient);
        }
    }

    @Test
    @DisplayName("STOMP 연결 종료 시 다른 구독자에게 퇴장 메시지 자동 발행")
    void disconnectPublishesLeaveMessage() throws Exception {

        // given
        TestUser testUser =
                createAuthenticatedUser();

        Long roomId =
                createChatRoom(
                        testUser.accessToken(),
                        "disconnect-room-"
                                + createUniqueValue()
                );

        addChatRoomMember(
                testUser.accessToken(),
                roomId,
                testUser.userId()
        );

        WebSocketStompClient observerClient =
                stompHelper.createStompClient();

        WebSocketStompClient disconnectClient =
                stompHelper.createStompClient();

        StompSession observerSession = null;
        StompSession disconnectSession = null;

        try {
            observerSession =
                    stompHelper.connect(
                            observerClient,
                            testUser.accessToken()
                    );

            disconnectSession =
                    stompHelper.connect(
                            disconnectClient,
                            testUser.accessToken()
                    );

            BlockingQueue<SendChatMessageResponse> observerMessages =
                    new LinkedBlockingQueue<>();

            BlockingQueue<SendChatMessageResponse> disconnectMessages =
                    new LinkedBlockingQueue<>();

            stompHelper.subscribe(
                    observerSession,
                    roomId,
                    observerMessages
            );

            stompHelper.subscribe(
                    disconnectSession,
                    roomId,
                    disconnectMessages
            );

            /*
             * 두 세션의 구독으로 발생한 ENTER 메시지를 제거한다.
             */
            stompHelper.drainPresenceMessages(
                    observerMessages
            );

            // when
            stompHelper.disconnect(
                    disconnectSession
            );

            disconnectSession = null;

            SendChatMessageResponse response =
                    stompHelper.awaitMessageType(
                            observerMessages,
                            MessageType.LEAVE
                    );

            // then
            assertThat(response)
                    .isNotNull();

            assertThat(response.type())
                    .isEqualTo(MessageType.LEAVE);

            assertThat(response.roomId())
                    .isEqualTo(roomId);

            assertThat(response.senderId())
                    .isEqualTo(testUser.userId());

            assertThat(response.senderNickname())
                    .isEqualTo(testUser.nickname());

            assertThat(response.message())
                    .isEqualTo(
                            testUser.nickname()
                                    + "님이 퇴장했습니다."
                    );

            assertThat(response.sendTime())
                    .isNotNull();

        } finally {
            stompHelper.disconnect(disconnectSession);
            stompHelper.disconnect(observerSession);

            stompHelper.stop(disconnectClient);
            stompHelper.stop(observerClient);
        }
    }
}