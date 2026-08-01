package com.planb.global.websocket.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.WebSocketExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.auth.AuthPrincipal;
import com.planb.global.security.provider.JwtAuthenticationProvider;
import com.planb.query.chat.service.ChatRoomMemberQueryService;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompChannelInterceptor implements ChannelInterceptor {

    private final JwtAuthenticationProvider jwtAuthenticationProvider;
    private final ChatRoomMemberQueryService chatRoomMemberQueryService;

    @Override
    public @Nullable Message<?> preSend(Message<?> message,
                                        MessageChannel channel) {

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );

        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        log.info(
                "STOMP INBOUND - command: {}, sessionId: {}, destination: {}, user: {}",
                accessor.getCommand(),
                accessor.getSessionId(),
                accessor.getDestination(),
                accessor.getUser() != null
                        ? accessor.getUser().getName()
                        : null
        );

        switch (accessor.getCommand()) {

            case CONNECT ->
                    validateConnection(accessor);

            case SUBSCRIBE ->
                    validateSubscription(accessor);

            case SEND ->
                    validateSend(accessor);

            default -> {
            }
        }

        return message;
    }

    private void validateConnection(StompHeaderAccessor accessor) {

        String authorizationHeader =
                accessor.getFirstNativeHeader("Authorization");

        Authentication authentication =
                jwtAuthenticationProvider.authenticate(authorizationHeader);

        accessor.setUser(authentication);

        log.info(
                "STOMP CONNECT AUTH SUCCESS - sessionId={}, user={}, authenticationType={}",
                accessor.getSessionId(),
                accessor.getUser() != null
                        ? accessor.getUser().getName()
                        : null,
                accessor.getUser() != null
                        ? accessor.getUser().getClass().getName()
                        : null
        );
    }

    private void validateSubscription(StompHeaderAccessor accessor) {

        log.info(
                "VALIDATE SUBSCRIBE START - destination={}, user={}",
                accessor.getDestination(),
                accessor.getUser() != null
                        ? accessor.getUser().getName()
                        : null
        );

        Long roomId = extractSubscribeRoomId(accessor);
        Long userId = extractUserId(accessor);

        log.info(
                "VALIDATE SUBSCRIBE PARSED - roomId={}, userId={}",
                roomId,
                userId
        );

        validateChatRoomMember(roomId, userId);
    }

    private void validateSend(StompHeaderAccessor accessor) {

        log.info(
                "VALIDATE SEND START - destination={}, user={}",
                accessor.getDestination(),
                accessor.getUser() != null
                        ? accessor.getUser().getName()
                        : null
        );

        Long roomId = extractSendRoomId(accessor);
        Long userId = extractUserId(accessor);

        log.info(
                "VALIDATE SEND PARSED - roomId={}, userId={}",
                roomId,
                userId
        );

        validateChatRoomMember(roomId, userId);
    }

    private void validateChatRoomMember(Long roomId,
                                        Long userId) {

        boolean matched =
                chatRoomMemberQueryService.checkSubscriberWithRoomId(
                        roomId,
                        userId
                );

        log.info(
                "VALIDATE CHAT MEMBER RESULT - roomId={}, userId={}, matched={}",
                roomId,
                userId,
                matched
        );

        if (!matched) {
            throw new BaseException(
                    WebSocketExceptionEnum.SUBSCRIBER_NOT_MATCHED
            );
        }
    }

    /**
     * SUBSCRIBE
     * /sub/api/v1/chat/{roomId}
     */
    private Long extractSubscribeRoomId(StompHeaderAccessor accessor) {

        String destination = accessor.getDestination();

        if (destination == null) {
            throw new BaseException(
                    WebSocketExceptionEnum.CHATROOM_NOT_FOUND
            );
        }

        return Long.parseLong(
                destination.substring(destination.lastIndexOf("/") + 1)
        );
    }

    /**
     * SEND
     * /pub/api/v1/chat/{roomId}/send
     */
    private Long extractSendRoomId(StompHeaderAccessor accessor) {

        String destination = accessor.getDestination();

        if (destination == null) {
            throw new BaseException(
                    WebSocketExceptionEnum.CHATROOM_NOT_FOUND
            );
        }

        String[] paths = destination.split("/");

        return Long.parseLong(paths[paths.length - 2]);
    }

    private Long extractUserId(StompHeaderAccessor accessor) {

        Authentication authentication =
                (Authentication) accessor.getUser();

        if (authentication == null) {
            throw new BaseException(
                    BaseExceptionEnum.USER_UNAUTHORIZED
            );
        }

        AuthPrincipal principal =
                (AuthPrincipal) authentication.getPrincipal();

        return principal.getUserId();
    }
}