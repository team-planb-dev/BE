package com.planb.domain.chat.websocket.listener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;
import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.facade.ChatFacade;
import com.planb.domain.chat.websocket.ResolvedSubscription;
import com.planb.domain.chat.websocket.registry.ChatSubscriptionRegistry;
import com.planb.domain.chat.websocket.resolver.ChatDestinationResolver;

import java.security.Principal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatSessionEventListener {

    private final ChatSubscriptionRegistry subscriptionRegistry;
    private final ChatDestinationResolver destinationResolver;
    private final ChatFacade chatFacade;

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        log.info(
                "SUBSCRIBE EVENT - sessionId: {}, destination: {}, subscriptionId: {}",
                accessor.getSessionId(),
                accessor.getDestination(),
                accessor.getSubscriptionId()
        );


        resolveSubscription(accessor)
                .filter(subscription ->
                        subscriptionRegistry.subscribe(
                                subscription.sessionId(),
                                subscription.subscriptionId(),
                                subscription.roomId(),
                                subscription.username()
                        )
                )
                .ifPresent(subscription -> {
                    log.info(
                            "ENTER PUBLISH roomId={}, username={}",
                            subscription.roomId(),
                            subscription.username()
                    );
                        publishPresenceMessage(
                                subscription.roomId(),
                                subscription.username(),
                                MessageType.ENTER
                        );
                });
    }

    @EventListener
    public void handleUnsubscribe(SessionUnsubscribeEvent event) {

        StompHeaderAccessor accessor =
                StompHeaderAccessor.wrap(event.getMessage());

        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();

        if (sessionId == null || subscriptionId == null) {
            return;
        }

        Optional.ofNullable(
                        subscriptionRegistry.unsubscribe(
                                sessionId,
                                subscriptionId
                        )
                )
                .ifPresent(subscription -> {

                    log.info(
                            "LEAVE PUBLISH roomId={}, username={}",
                            subscription.roomId(),
                            subscription.username()
                    );

                    publishPresenceMessage(
                            subscription.roomId(),
                            subscription.username(),
                            MessageType.LEAVE
                    );
                });
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {

        subscriptionRegistry.disconnect(event.getSessionId())
                .forEach(subscription ->
                        publishPresenceMessage(
                                subscription.roomId(),
                                subscription.username(),
                                MessageType.LEAVE
                        )
                );
    }

    // STOMP 헤더로부터 구독 정보추출
    private Optional<ResolvedSubscription> resolveSubscription(
            StompHeaderAccessor accessor
    ) {

        String sessionId = accessor.getSessionId();
        String subscriptionId = accessor.getSubscriptionId();
        Principal principal = accessor.getUser();

        Long roomId = destinationResolver.extractRoomId(
                accessor.getDestination()
        );

        if (sessionId == null
                || subscriptionId == null
                || principal == null
                || roomId == null) {

            return Optional.empty();
        }

        return Optional.of(
                new ResolvedSubscription(
                        sessionId,
                        subscriptionId,
                        roomId,
                        principal.getName()
                )
        );
    }

    // 메시지를 생성하는 메소드
    private void publishPresenceMessage(
            Long roomId,
            String username,
            MessageType messageType
    ) {

        log.info(
                "SYSTEM MESSAGE roomId={}, username={}, type={}",
                roomId,
                username,
                messageType
        );

        chatFacade.publishSystemMessage(
                roomId,
                username,
                messageType
        );
    }
}