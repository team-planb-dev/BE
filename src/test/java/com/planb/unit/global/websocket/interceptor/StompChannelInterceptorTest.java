package com.planb.unit.global.websocket.interceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;

import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.auth.AuthPrincipal;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.provider.JwtAuthenticationProvider;
import com.planb.global.websocket.interceptor.StompChannelInterceptor;
import com.planb.query.chat.service.ChatRoomMemberQueryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompChannelInterceptorTest {

    @Mock
    private JwtAuthenticationProvider jwtAuthenticationProvider;

    @Mock
    private ChatRoomMemberQueryService chatRoomMemberQueryService;

    @Mock
    private MessageChannel messageChannel;

    @InjectMocks
    private StompChannelInterceptor stompChannelInterceptor;

    @Test
    @DisplayName("CONNECT 요청일 경우, Authorization 헤더 검증")
    void preSendConnectSuccess() {

        // given
        String authorizationHeader = "Bearer access-token";

        Authentication authentication = createAuthentication();

        StompHeaderAccessor accessor = StompHeaderAccessor
                .create(StompCommand
                        .CONNECT);

        accessor.setNativeHeader(
                "Authorization",
                authorizationHeader
        );

        Message<byte[]> message = createMessage(accessor);

        when(jwtAuthenticationProvider
                .authenticate(authorizationHeader))
                .thenReturn(authentication);

        // when
        Message<?> result =
                stompChannelInterceptor.preSend(
                        message,
                        messageChannel
                );

        // then
        assertThat(result).isSameAs(message);

        verify(jwtAuthenticationProvider).authenticate(authorizationHeader);

        verify(chatRoomMemberQueryService, never())
                .checkSubscriberWithRoomId(
                        anyLong(),
                        anyLong()
                );
    }

    @Test
    @DisplayName("CONNECT 인증 실패 시, 예외 발생")
    void preSendConnectFail() {

        // given
        String authorizationHeader = "Bearer invalid-token";

        StompHeaderAccessor accessor = StompHeaderAccessor
                .create(StompCommand
                        .CONNECT);

        accessor.setNativeHeader(
                "Authorization",
                authorizationHeader
        );

        Message<byte[]> message = createMessage(accessor);

        BaseException baseException = mock(BaseException.class);

        when(jwtAuthenticationProvider
                .authenticate(authorizationHeader))
                .thenThrow(baseException);

        // when & then
        assertThatThrownBy(
                () -> stompChannelInterceptor.preSend(
                        message,
                        messageChannel
                )
        )
                .isInstanceOf(BaseException.class);

        verify(jwtAuthenticationProvider).authenticate(authorizationHeader);

        verify(chatRoomMemberQueryService, never())
                .checkSubscriberWithRoomId(
                        anyLong(),
                        anyLong()
                );
    }

    @Test
    @DisplayName("SUBSCRIBE 요청의 사용자가 채팅방 멤버일 경우, 통과")
    void preSendSubscribeSuccess() {

        // given
        Long roomId = 1L;
        Long userId = 1L;

        StompHeaderAccessor accessor = StompHeaderAccessor
                .create(StompCommand
                        .SUBSCRIBE);

        accessor.setDestination(
                "/sub/api/v1/chat/" + roomId
        );

        accessor.setUser(createAuthentication());

        Message<byte[]> message = createMessage(accessor);

        when(chatRoomMemberQueryService
                .checkSubscriberWithRoomId(roomId, userId))
                .thenReturn(true);

        // when
        Message<?> result =
                stompChannelInterceptor.preSend(
                        message,
                        messageChannel
                );

        // then
        assertThat(result).isSameAs(message);

        verify(chatRoomMemberQueryService).checkSubscriberWithRoomId(roomId, userId);

        verify(jwtAuthenticationProvider, never())
                .authenticate(anyString());
    }

    @Test
    @DisplayName("SEND 요청의 사용자가 채팅방 멤버일 경우, 통과")
    void preSendSendSuccess() {

        // given
        Long roomId = 1L;
        Long userId = 1L;

        StompHeaderAccessor accessor = StompHeaderAccessor
                .create(StompCommand
                        .SEND);

        accessor.setDestination(
                "/pub/api/v1/chat/" + roomId
        );

        accessor.setUser(createAuthentication());

        Message<byte[]> message = createMessage(accessor);

        when(chatRoomMemberQueryService
                .checkSubscriberWithRoomId(roomId, userId))
                .thenReturn(true);

        // when
        Message<?> result =
                stompChannelInterceptor.preSend(
                        message,
                        messageChannel
                );

        // then
        assertThat(result).isSameAs(message);

        verify(chatRoomMemberQueryService).checkSubscriberWithRoomId(roomId, userId);

        verify(jwtAuthenticationProvider, never())
                .authenticate(anyString());
    }

    @Test
    @DisplayName("채팅방 멤버가 아닐 경우, SUBSCRIBE 요청에 실패")
    void preSendSubscribeFailWhenSubscriberNotMatched() {

        // given
        Long roomId = 1L;
        Long userId = 1L;

        StompHeaderAccessor accessor = StompHeaderAccessor
                .create(StompCommand
                        .SUBSCRIBE);

        accessor.setDestination(
                "/sub/api/v1/chat/" + roomId
        );

        accessor.setUser(createAuthentication());

        Message<byte[]> message = createMessage(accessor);

        when(chatRoomMemberQueryService
                .checkSubscriberWithRoomId(roomId, userId))
                .thenReturn(false);


        // when & then
        assertThatThrownBy(
                () -> stompChannelInterceptor.preSend(
                        message,
                        messageChannel
                )
        )
                .isInstanceOf(BaseException.class);

        verify(chatRoomMemberQueryService).checkSubscriberWithRoomId(roomId, userId);
    }

    @Test
    @DisplayName("채팅방 멤버가 아닐 경우, SEND 요청에 실패")
    void preSendSendFailWhenSubscriberNotMatched() {

        // given
        Long roomId = 1L;
        Long userId = 1L;

        StompHeaderAccessor accessor = StompHeaderAccessor
                .create(StompCommand
                        .SEND);

        accessor.setDestination(
                "/pub/api/v1/chat/" + roomId
        );

        accessor.setUser(createAuthentication());

        Message<byte[]> message = createMessage(accessor);

        when(chatRoomMemberQueryService
                .checkSubscriberWithRoomId(roomId, userId))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(
                () -> stompChannelInterceptor.preSend(
                        message,
                        messageChannel
                )
        )
                .isInstanceOf(BaseException.class);

        verify(chatRoomMemberQueryService).checkSubscriberWithRoomId(roomId, userId);
    }

    @Test
    @DisplayName("destination이 없을 경우, 구독 요청에 실패")
    void preSendSubscribeFailWhenDestinationIsNull() {

        // given
        StompHeaderAccessor accessor = StompHeaderAccessor
                .create(StompCommand
                        .SUBSCRIBE);

        accessor.setUser(createAuthentication());

        Message<byte[]> message = createMessage(accessor);

        // when & then
        assertThatThrownBy(
                () -> stompChannelInterceptor.preSend(
                        message,
                        messageChannel
                )
        )
                .isInstanceOf(BaseException.class);

        verify(chatRoomMemberQueryService, never())
                .checkSubscriberWithRoomId(
                        anyLong(),
                        anyLong()
                );
    }

    @Test
    @DisplayName("인증 정보가 없을 경우, 구독 요청에 실패")
    void preSendSubscribeFailWhenAuthenticationIsNull() {

        // given
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.SUBSCRIBE);

        accessor.setDestination(
                "/sub/api/v1/chat/1"
        );

        Message<byte[]> message = createMessage(accessor);

        // when & then
        assertThatThrownBy(
                () -> stompChannelInterceptor.preSend(
                        message,
                        messageChannel
                )
        )
                .isInstanceOf(BaseException.class);

        verify(chatRoomMemberQueryService, never())
                .checkSubscriberWithRoomId(
                        anyLong(),
                        anyLong()
                );
    }

    @Test
    @DisplayName("STOMP 명령어가 없을 경우, 원본 메시지를 반환")
    void preSendReturnOriginalMessageWhenCommandIsNull() {

        // given
        Message<byte[]> message = MessageBuilder
                .withPayload(new byte[0])
                .build();

        // when
        Message<?> result =
                stompChannelInterceptor.preSend(
                        message,
                        messageChannel
                );

        // then
        assertThat(result).isSameAs(message);

        verify(jwtAuthenticationProvider, never())
                .authenticate(anyString());

        verify(chatRoomMemberQueryService, never())
                .checkSubscriberWithRoomId(
                        anyLong(),
                        anyLong()
                );
    }

    @Test
    @DisplayName("DISCONNECT 요청일 경우, 별도의 검증 없이 통과")
    void preSendDisconnectSuccess() {

        // given
        StompHeaderAccessor accessor =
                StompHeaderAccessor.create(StompCommand.DISCONNECT);

        Message<byte[]> message = createMessage(accessor);

        // when
        Message<?> result =
                stompChannelInterceptor.preSend(
                        message,
                        messageChannel
                );

        // then
        assertThat(result).isSameAs(message);

        verify(jwtAuthenticationProvider, never()).authenticate(anyString());

        verify(chatRoomMemberQueryService, never())
                .checkSubscriberWithRoomId(
                        anyLong(),
                        anyLong()
                );
    }


    /**
     *
     * 헬퍼 메서드 모음
     */

    private Authentication createAuthentication() {
        UserAuthCache userAuthCache = new UserAuthCache(
                1L,
                "testUser@example.com",
                "ROLE_USER"
        );

        AuthPrincipal authPrincipal =
                new AuthPrincipal(userAuthCache);

        return new UsernamePasswordAuthenticationToken(
                authPrincipal,
                null,
                authPrincipal.getAuthorities()
        );
    }

    private Message<byte[]> createMessage(
            StompHeaderAccessor accessor
    ) {
        return MessageBuilder.createMessage(
                new byte[0],
                accessor.getMessageHeaders()
        );
    }
}