package com.planb.unit.domain.chat.websocket.registry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.planb.domain.chat.websocket.ChatSubscriptionInfo;
import com.planb.domain.chat.websocket.registry.ChatSubscriptionRegistry;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatSubscriptionRegistryTest {

    private ChatSubscriptionRegistry chatSubscriptionRegistry;

    @BeforeEach
    void setUp() {
        chatSubscriptionRegistry = new ChatSubscriptionRegistry();
    }

    @Test
    @DisplayName("새로운 구독 정보를 등록 시, true 반환")
    void subscribeSuccess() {

        // given
        String sessionId = "session-1";

        String subscriptionId = "subscription-1";

        Long roomId = 1L;

        String username = "testUser";

        // when
        boolean result = chatSubscriptionRegistry.subscribe(
                sessionId,
                subscriptionId,
                roomId,
                username
        );

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("같은 세션과 구독 ID를 다시 등록 시, false 반환")
    void subscribeFailWhenDuplicateSubscriptionExists() {

        // given
        String sessionId = "session-1";
        String subscriptionId = "subscription-1";

        chatSubscriptionRegistry.subscribe(
                sessionId,
                subscriptionId,
                1L,
                "testUser"
        );

        // when
        boolean result = chatSubscriptionRegistry.subscribe(
                sessionId,
                subscriptionId,
                2L,
                "anotherUser"
        );

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("같은 세션에서 서로 다른 구독 ID는 각각 등록 가능")
    void subscribeSuccessWithDifferentSubscriptionId() {

        // given
        String sessionId = "session-1";

        // when
        boolean firstResult = chatSubscriptionRegistry.subscribe(
                sessionId,
                "subscription-1",
                1L,
                "testUser"
        );

        boolean secondResult = chatSubscriptionRegistry.subscribe(
                sessionId,
                "subscription-2",
                2L,
                "testUser"
        );

        // then
        assertThat(firstResult).isTrue();
        assertThat(secondResult).isTrue();
    }

    @Test
    @DisplayName("서로 다른 세션에서는 같은 구독 ID를 사용 가능")
    void subscribeSuccessWithDifferentSessionId() {

        // given
        String subscriptionId = "subscription-1";

        // when
        boolean firstResult = chatSubscriptionRegistry.subscribe(
                "session-1",
                subscriptionId,
                1L,
                "userA"
        );

        boolean secondResult = chatSubscriptionRegistry.subscribe(
                "session-2",
                subscriptionId,
                2L,
                "userB"
        );

        // then
        assertThat(firstResult).isTrue();
        assertThat(secondResult).isTrue();
    }

    @Test
    @DisplayName("등록된 구독을 해제 시, 구독 정보 반환")
    void unsubscribeSuccess() {

        // given
        String sessionId = "session-1";

        String subscriptionId = "subscription-1";

        Long roomId = 1L;

        String username = "testUser";

        chatSubscriptionRegistry.subscribe(
                sessionId,
                subscriptionId,
                roomId,
                username
        );

        // when
        ChatSubscriptionInfo result =
                chatSubscriptionRegistry.unsubscribe(
                        sessionId,
                        subscriptionId
                );

        // then
        assertThat(result).isNotNull();

        assertThat(result
                .roomId())
                .isEqualTo(roomId);

        assertThat(result
                .username())
                .isEqualTo(username);
    }

    @Test
    @DisplayName("존재하지 않는 세션의 구독을 해제 시, null 반환")
    void unsubscribeReturnNullWhenSessionDoesNotExist() {

        // when
        ChatSubscriptionInfo result =
                chatSubscriptionRegistry.unsubscribe(
                        "unknown-session",
                        "subscription-1"
                );

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 구독 ID를 해제 시, null 반환")
    void unsubscribeReturnNullWhenSubscriptionDoesNotExist() {

        // given
        String sessionId = "session-1";

        chatSubscriptionRegistry.subscribe(
                sessionId,
                "subscription-1",
                1L,
                "testUser"
        );

        // when
        ChatSubscriptionInfo result =
                chatSubscriptionRegistry.unsubscribe(
                        sessionId,
                        "unknown-subscription"
                );

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("한 구독을 해제 시, 같은 세션의 다른 구독은 유지")
    void unsubscribeDoesNotRemoveOtherSubscription() {

        // given
        String sessionId = "session-1";

        chatSubscriptionRegistry.subscribe(
                sessionId,
                "subscription-1",
                1L,
                "testUser"
        );

        chatSubscriptionRegistry.subscribe(
                sessionId,
                "subscription-2",
                2L,
                "testUser"
        );

        // when
        ChatSubscriptionInfo removed =
                chatSubscriptionRegistry.unsubscribe(
                        sessionId,
                        "subscription-1"
                );

        ChatSubscriptionInfo remaining =
                chatSubscriptionRegistry.unsubscribe(
                        sessionId,
                        "subscription-2"
                );

        // then
        assertThat(removed).isNotNull();

        assertThat(removed
                .roomId())
                .isEqualTo(1L);

        assertThat(remaining).isNotNull();

        assertThat(remaining
                .roomId())
                .isEqualTo(2L);
    }

    @Test
    @DisplayName("마지막 구독을 해제한 세션에는 새로운 구독을 다시 등록 가능")
    void subscribeSuccessAfterRemovingLastSubscription() {

        // given
        String sessionId = "session-1";
        String subscriptionId = "subscription-1";

        chatSubscriptionRegistry.subscribe(
                sessionId,
                subscriptionId,
                1L,
                "testUser"
        );

        chatSubscriptionRegistry.unsubscribe(
                sessionId,
                subscriptionId
        );

        // when
        boolean result = chatSubscriptionRegistry.subscribe(
                sessionId,
                subscriptionId,
                2L,
                "anotherUser"
        );

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("세션 연결 종료 시 해당 세션의 모든 구독 정보를 반환한다")
    void disconnectSuccess() {

        // given
        String sessionId = "session-1";

        chatSubscriptionRegistry.subscribe(
                sessionId,
                "subscription-1",
                1L,
                "testUser"
        );

        chatSubscriptionRegistry.subscribe(
                sessionId,
                "subscription-2",
                2L,
                "testUser"
        );

        // when
        List<ChatSubscriptionInfo> result =
                chatSubscriptionRegistry.disconnect(sessionId);

        // then
        assertThat(result).hasSize(2);

        assertThat(result)
                .extracting(ChatSubscriptionInfo::roomId)
                .containsExactlyInAnyOrder(1L, 2L);

        assertThat(result)
                .extracting(ChatSubscriptionInfo::username)
                .containsOnly("testUser");
    }

    @Test
    @DisplayName("세션 연결 종료 후, 해당 세션의 구독 정보는 제거")
    void disconnectRemovesSessionSubscriptions() {

        // given
        String sessionId = "session-1";

        chatSubscriptionRegistry.subscribe(
                sessionId,
                "subscription-1",
                1L,
                "testUser"
        );

        chatSubscriptionRegistry.disconnect(sessionId);

        // when
        ChatSubscriptionInfo result =
                chatSubscriptionRegistry.unsubscribe(
                        sessionId,
                        "subscription-1"
                );

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("세션 연결 종료 후 같은 세션을 다시 종료 시, 빈 리스트 반환")
    void disconnectReturnEmptyListAfterSessionRemoved() {

        // given
        String sessionId = "session-1";

        chatSubscriptionRegistry.subscribe(
                sessionId,
                "subscription-1",
                1L,
                "testUser"
        );

        chatSubscriptionRegistry.disconnect(sessionId);

        // when
        List<ChatSubscriptionInfo> result =
                chatSubscriptionRegistry.disconnect(sessionId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 세션을 종료 시, 빈 리스트 반환")
    void disconnectReturnEmptyListWhenSessionDoesNotExist() {

        // when
        List<ChatSubscriptionInfo> result =
                chatSubscriptionRegistry.disconnect("unknown-session");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("세션 연결 종료는 다른 세션의 구독에 영향 x")
    void disconnectDoesNotRemoveOtherSessionSubscriptions() {

        // given
        chatSubscriptionRegistry.subscribe(
                "session-1",
                "subscription-1",
                1L,
                "userA"
        );

        chatSubscriptionRegistry.subscribe(
                "session-2",
                "subscription-1",
                2L,
                "userB"
        );

        // when
        List<ChatSubscriptionInfo> disconnected =
                chatSubscriptionRegistry.disconnect("session-1");

        ChatSubscriptionInfo remaining =
                chatSubscriptionRegistry.unsubscribe(
                        "session-2",
                        "subscription-1"
                );

        // then
        assertThat(disconnected).hasSize(1);

        assertThat(disconnected
                .get(0)
                .roomId())
                .isEqualTo(1L);

        assertThat(disconnected
                .get(0)
                .username())
                .isEqualTo("userA");

        assertThat(remaining).isNotNull();

        assertThat(remaining
                .roomId())
                .isEqualTo(2L);

        assertThat(remaining
                .username())
                .isEqualTo("userB");
    }
}