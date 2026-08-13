package com.planb.unit.domain.chat.websocket.listener;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.facade.ChatFacade;
import com.planb.domain.chat.websocket.ChatSubscriptionInfo;
import com.planb.domain.chat.websocket.listener.ChatSessionEventListener;
import com.planb.domain.chat.websocket.registry.ChatSubscriptionRegistry;
import com.planb.domain.chat.websocket.resolver.ChatDestinationResolver;

import java.security.Principal;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatSessionEventListenerTest {

    @Mock
    private ChatSubscriptionRegistry subscriptionRegistry;

    @Mock
    private ChatDestinationResolver destinationResolver;

    @Mock
    private ChatFacade chatFacade;

    @Mock
    private Principal principal;

    @Mock
    private SessionSubscribeEvent subscribeEvent;

    @Mock
    private SessionUnsubscribeEvent unsubscribeEvent;

    @Mock
    private SessionDisconnectEvent disconnectEvent;

    @InjectMocks
    private ChatSessionEventListener chatSessionEventListener;

    @Test
    @DisplayName("채팅방 구독 성공 시, ENTER 메시지를 발행")
    void handleSubscribeSuccess() {

        // given
        String sessionId = "session-1";
        String subscriptionId = "subscription-1";
        String destination = "/sub/api/v1/chat/1";
        String username = "testUser@exmaple.com";
        Long roomId = 1L;

        Message<byte[]> message = createSubscribeMessage(
                sessionId,
                subscriptionId,
                destination,
                principal
        );

        when(subscribeEvent
                .getMessage())
                .thenReturn(message);

        when(principal
                .getName())
                .thenReturn(username);

        when(destinationResolver
                .extractRoomId(destination))
                .thenReturn(roomId);

        when(subscriptionRegistry.subscribe(
                sessionId,
                subscriptionId,
                roomId,
                username
        )).thenReturn(true);

        // when
        chatSessionEventListener.handleSubscribe(subscribeEvent);

        // then
        verify(destinationResolver).extractRoomId(destination);

        verify(subscriptionRegistry).subscribe(
                sessionId,
                subscriptionId,
                roomId,
                username
        );

        verify(chatFacade).publishSystemMessage(
                roomId,
                username,
                MessageType.ENTER
        );
    }

    @Test
    @DisplayName("이미 등록된 구독일 경우, ENTER 메시지 발행x")
    void handleSubscribeDoesNotPublishWhenAlreadySubscribed() {

        // given
        String sessionId = "session-1";

        String subscriptionId = "subscription-1";

        String destination = "/sub/api/v1/chat/1";

        String username = "testUser@exmaple.com";

        Long roomId = 1L;

        Message<byte[]> message = createSubscribeMessage(
                sessionId,
                subscriptionId,
                destination,
                principal
        );

        when(subscribeEvent
                .getMessage())
                .thenReturn(message);

        when(principal
                .getName())
                .thenReturn(username);

        when(destinationResolver
                .extractRoomId(destination))
                .thenReturn(roomId);

        when(subscriptionRegistry.subscribe(
                sessionId,
                subscriptionId,
                roomId,
                username
        )).thenReturn(false);

        // when
        chatSessionEventListener.handleSubscribe(subscribeEvent);

        // then
        verify(subscriptionRegistry).subscribe(
                sessionId,
                subscriptionId,
                roomId,
                username
        );

        verify(chatFacade, never()).publishSystemMessage(
                anyLong(),
                anyString(),
                any(MessageType.class)
        );
    }

    @Test
    @DisplayName("구독 요청에 세션 ID가 없을 경우, 구독 등록x")
    void handleSubscribeDoesNothingWhenSessionIdIsNull() {

        // given
        String destination = "/sub/api/v1/chat/1";

        Message<byte[]> message = createSubscribeMessage(
                null,
                "subscription-1",
                destination,
                principal
        );

        when(subscribeEvent
                .getMessage())
                .thenReturn(message);

        when(destinationResolver
                .extractRoomId(destination))
                .thenReturn(1L);

        // when
        chatSessionEventListener.handleSubscribe(subscribeEvent);

        // then
        verify(subscriptionRegistry, never()).subscribe(
                anyString(),
                anyString(),
                anyLong(),
                anyString()
        );

        verify(chatFacade, never()).publishSystemMessage(
                anyLong(),
                anyString(),
                any(MessageType.class)
        );
    }

    @Test
    @DisplayName("구독 요청에 구독 ID가 없을 경우, 구독 등록x")
    void handleSubscribeDoesNothingWhenSubscriptionIdIsNull() {
        // given
        String destination = "/sub/api/v1/chat/1";

        Message<byte[]> message = createSubscribeMessage(
                "session-1",
                null,
                destination,
                principal
        );

        when(subscribeEvent
                .getMessage())
                .thenReturn(message);

        when(destinationResolver
                .extractRoomId(destination))
                .thenReturn(1L);

        // when
        chatSessionEventListener.handleSubscribe(subscribeEvent);

        // then
        verify(subscriptionRegistry, never()).subscribe(
                anyString(),
                anyString(),
                anyLong(),
                anyString()
        );

        verify(chatFacade, never()).publishSystemMessage(
                anyLong(),
                anyString(),
                any(MessageType.class)
        );
    }

    @Test
    @DisplayName("구독 요청에 인증 사용자가 없을 경우, 구독 등록x")
    void handleSubscribeDoesNothingWhenPrincipalIsNull() {
        // given
        String destination = "/sub/api/v1/chat/1";

        Message<byte[]> message = createSubscribeMessage(
                "session-1",
                "subscription-1",
                destination,
                null
        );

        when(subscribeEvent
                .getMessage())
                .thenReturn(message);

        when(destinationResolver
                .extractRoomId(destination))
                .thenReturn(1L);

        // when
        chatSessionEventListener.handleSubscribe(subscribeEvent);

        // then
        verify(subscriptionRegistry, never()).subscribe(
                anyString(),
                anyString(),
                anyLong(),
                anyString()
        );

        verify(chatFacade, never()).publishSystemMessage(
                anyLong(),
                anyString(),
                any(MessageType.class)
        );
    }

    @Test
    @DisplayName("구독 destination에서 채팅방 id를 추출하지 못할 경우, 구독 등록x")
    void handleSubscribeDoesNothingWhenRoomIdIsNull() {

        // given
        String destination = "/invalid/destination";

        Message<byte[]> message = createSubscribeMessage(
                "session-1",
                "subscription-1",
                destination,
                principal
        );

        when(subscribeEvent
                .getMessage())
                .thenReturn(message);

        when(destinationResolver
                .extractRoomId(destination))
                .thenReturn(null);

        // when
        chatSessionEventListener.handleSubscribe(subscribeEvent);

        // then
        verify(subscriptionRegistry, never()).subscribe(
                anyString(),
                anyString(),
                anyLong(),
                anyString()
        );

        verify(chatFacade, never()).publishSystemMessage(
                anyLong(),
                anyString(),
                any(MessageType.class)
        );
    }

    @Test
    @DisplayName("채팅방 구독 해제 성공 시, LEAVE 메시지 발행")
    void handleUnsubscribeSuccess() {

        // given
        String sessionId = "session-1";

        String subscriptionId = "subscription-1";

        String username = "testUser@example.com";

        Long roomId = 1L;

        ChatSubscriptionInfo subscriptionInfo =
                new ChatSubscriptionInfo(
                        roomId,
                        username
                );

        Message<byte[]> message = createUnsubscribeMessage(
                sessionId,
                subscriptionId
        );

        when(unsubscribeEvent
                .getMessage())
                .thenReturn(message);

        when(subscriptionRegistry.unsubscribe(
                sessionId,
                subscriptionId
        )).thenReturn(subscriptionInfo);

        // when
        chatSessionEventListener.handleUnsubscribe(unsubscribeEvent);

        // then
        verify(subscriptionRegistry).unsubscribe(
                sessionId,
                subscriptionId
        );

        verify(chatFacade).publishSystemMessage(
                roomId,
                username,
                MessageType.LEAVE
        );
    }

    @Test
    @DisplayName("구독 해제 정보가 Registry에 없을 경우, LEAVE 메시지 발행x")
    void handleUnsubscribeDoesNotPublishWhenSubscriptionDoesNotExist() {

        // given
        String sessionId = "session-1";

        String subscriptionId = "subscription-1";

        Message<byte[]> message = createUnsubscribeMessage(
                sessionId,
                subscriptionId
        );

        when(unsubscribeEvent
                .getMessage())
                .thenReturn(message);

        when(subscriptionRegistry.unsubscribe(
                sessionId,
                subscriptionId
        )).thenReturn(null);

        // when
        chatSessionEventListener.handleUnsubscribe(unsubscribeEvent);

        // then
        verify(subscriptionRegistry).unsubscribe(
                sessionId,
                subscriptionId
        );

        verify(chatFacade, never()).publishSystemMessage(
                anyLong(),
                anyString(),
                any(MessageType.class)
        );
    }

    @Test
    @DisplayName("구독 해제 요청에 세션 ID가 없을 경우, 어떤 작업도 x")
    void handleUnsubscribeDoesNothingWhenSessionIdIsNull() {

        // given
        Message<byte[]> message = createUnsubscribeMessage(
                null,
                "subscription-1"
        );

        when(unsubscribeEvent
                .getMessage())
                .thenReturn(message);

        // when
        chatSessionEventListener.handleUnsubscribe(unsubscribeEvent);

        // then
        verify(subscriptionRegistry, never()).unsubscribe(
                anyString(),
                anyString()
        );

        verify(chatFacade, never()).publishSystemMessage(
                anyLong(),
                anyString(),
                any(MessageType.class)
        );
    }

    @Test
    @DisplayName("구독 해제 요청에 구독 ID가 없을 경우, 어떤 작업도 x")
    void handleUnsubscribeDoesNothingWhenSubscriptionIdIsNull() {

        // given
        Message<byte[]> message = createUnsubscribeMessage(
                "session-1",
                null
        );

        when(unsubscribeEvent
                .getMessage())
                .thenReturn(message);

        // when
        chatSessionEventListener.handleUnsubscribe(unsubscribeEvent);

        // then
        verify(subscriptionRegistry, never()).unsubscribe(
                anyString(),
                anyString()
        );

        verify(chatFacade, never()).publishSystemMessage(
                anyLong(),
                anyString(),
                any(MessageType.class)
        );
    }

    @Test
    @DisplayName("세션 연결 종료 시, 해당 세션의 모든 구독에 LEAVE 메시지 발행")
    void handleDisconnectSuccess() {

        // given
        String sessionId = "session-1";

        ChatSubscriptionInfo firstSubscription =
                new ChatSubscriptionInfo(
                        1L,
                        "testUser@example.com"
                );

        ChatSubscriptionInfo secondSubscription =
                new ChatSubscriptionInfo(
                        2L,
                        "testUser2@example.com"
                );

        when(disconnectEvent
                .getSessionId())
                .thenReturn(sessionId);

        when(subscriptionRegistry.disconnect(sessionId))
                .thenReturn(List.of(
                        firstSubscription,
                        secondSubscription
                ));

        // when
        chatSessionEventListener.handleDisconnect(disconnectEvent);

        // then
        verify(subscriptionRegistry)
                .disconnect(sessionId);

        verify(chatFacade).publishSystemMessage(
                1L,
                "testUser@example.com",
                MessageType.LEAVE
        );

        verify(chatFacade).publishSystemMessage(
                2L,
                "testUser2@example.com",
                MessageType.LEAVE
        );
    }

    @Test
    @DisplayName("세션 연결 종료 시, 등록된 구독이 없을 경우 메시지 발행 x")
    void handleDisconnectDoesNotPublishWhenSubscriptionDoesNotExist() {

        // given
        String sessionId = "session-1";

        when(disconnectEvent
                .getSessionId())
                .thenReturn(sessionId);

        when(subscriptionRegistry
                .disconnect(sessionId))
                .thenReturn(List.of());

        // when
        chatSessionEventListener.handleDisconnect(disconnectEvent);

        // then
        verify(subscriptionRegistry)
                .disconnect(sessionId);

        verify(chatFacade, never()).publishSystemMessage(
                anyLong(),
                anyString(),
                any(MessageType.class)
        );
    }


    /**
     *
     * 헬퍼 메서드 모음
     */

    private Message<byte[]> createSubscribeMessage
            (String sessionId,
             String subscriptionId,
             String destination,
             Principal principal) {

        StompHeaderAccessor accessor = StompHeaderAccessor
                .create(StompCommand
                        .SUBSCRIBE);

        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);
        accessor.setDestination(destination);
        accessor.setUser(principal);

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }

    private Message<byte[]> createUnsubscribeMessage
            (String sessionId,
             String subscriptionId) {

        StompHeaderAccessor accessor = StompHeaderAccessor
                .create(StompCommand
                        .UNSUBSCRIBE);

        accessor.setSessionId(sessionId);
        accessor.setSubscriptionId(subscriptionId);

        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }
}