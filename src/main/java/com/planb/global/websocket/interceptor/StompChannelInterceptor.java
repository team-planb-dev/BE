package com.planb.global.websocket.interceptor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.simp.annotation.support.SimpAnnotationMethodMessageHandler;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.WebSocketExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.auth.AuthPrincipal;
import com.planb.global.security.provider.JwtAuthenticationProvider;
import com.planb.domain.chat.websocket.resolver.ChatDestinationResolver;
import com.planb.query.chat.service.ChatRoomMemberQueryService;

import java.util.Arrays;

@Component
@RequiredArgsConstructor
@Slf4j
public class StompChannelInterceptor implements ExecutorChannelInterceptor {

    private final JwtAuthenticationProvider jwtAuthenticationProvider;
    private final ChatRoomMemberQueryService chatRoomMemberQueryService;
    private final ChatDestinationResolver chatDestinationResolver;

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
                        ? accessor
                                .getUser()
                                .getName()
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

    // SEND 처리 스레드의 종류 기록, 장시간 AI 편집이 점유하는 실행기 확인
    @Override
    public Message<?> beforeHandle(
            Message<?> message,
            MessageChannel channel,
            MessageHandler handler
    ) {

        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(
                message,
                StompHeaderAccessor.class
        );

        // SEND 구독 처리기 중 컨트롤러 처리 작업만 기록, broker·사용자 경로 처리 제외
        if (accessor != null && accessor.getCommand() == StompCommand.SEND
                && handler instanceof SimpAnnotationMethodMessageHandler) {
            log.info(
                    "STOMP HANDLE - sessionId: {}, destination: {}, thread: {}, virtual: {}",
                    accessor.getSessionId(),
                    accessor.getDestination(),
                    Thread
                            .currentThread()
                            .getName(),
                    Thread
                            .currentThread()
                            .isVirtual()
            );
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
                "STOMP CONNECT AUTH SUCCESS - sessionId={}, user={}, authenticationType={}, clientHeartbeat={}",
                accessor.getSessionId(),
                accessor.getUser() != null
                        ? accessor
                                .getUser()
                                .getName()
                        : null,
                accessor.getUser() != null
                        ? accessor
                                .getUser()
                                .getClass()
                                .getName()
                        : null,
                Arrays.toString(
                        accessor.getHeartbeat()
                )
        );
    }

    private void validateSubscription(StompHeaderAccessor accessor) {

        log.info(
                "VALIDATE SUBSCRIBE START - destination={}, user={}",
                accessor.getDestination(),
                accessor.getUser() != null
                        ? accessor
                                .getUser()
                                .getName()
                        : null
        );

        Long roomId = chatDestinationResolver
                .extractRoomId(accessor.getDestination());

        if (roomId == null) {
            throw new BaseException(
                    WebSocketExceptionEnum.CHATROOM_NOT_FOUND
            );
        }
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
                        ? accessor
                                .getUser()
                                .getName()
                        : null
        );

        Long roomId = chatDestinationResolver
                .extractSendRoomId(accessor.getDestination());

        if (roomId == null) {
            throw new BaseException(
                    WebSocketExceptionEnum.CHATROOM_NOT_FOUND
            );
        }
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