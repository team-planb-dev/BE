package com.planb.unit.domain.chat.websocket.resolver;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.planb.domain.chat.websocket.resolver.ChatDestinationResolver;

import static org.assertj.core.api.Assertions.assertThat;


class ChatDestinationResolverTest {

    private final ChatDestinationResolver chatDestinationResolver =
            new ChatDestinationResolver();

    @Test
    @DisplayName("채팅방 id 추출 성공")
    void extractRoomIdSuccess() {

        // given
        String destination = "/sub/api/v1/chat/1";

        // when
        Long roomId = chatDestinationResolver.extractRoomId(destination);

        // then
        assertThat(roomId).isEqualTo(1L);
    }

    @Test
    @DisplayName("destination이 null이면, null 반환")
    void extractRoomIdWithNullDestination() {
        // when
        Long roomId = chatDestinationResolver.extractRoomId(null);

        // then
        assertThat(roomId).isNull();
    }

    @Test
    @DisplayName("잘못된 prefix이면, null 반환")
    void extractRoomIdWithInvalidPrefix() {
        // given
        String destination = "/pub/api/v1/chat/1";

        // when
        Long roomId = chatDestinationResolver.extractRoomId(destination);

        // then
        assertThat(roomId).isNull();
    }

    @Test
    @DisplayName("채팅방 id가 비어있으면, null 반환")
    void extractRoomIdWithBlankRoomId() {
        // given
        String destination = "/sub/api/v1/chat/";

        // when
        Long roomId = chatDestinationResolver.extractRoomId(destination);

        // then
        assertThat(roomId).isNull();
    }

    @Test
    @DisplayName("추가 경로가 포함될 경우, null 반환")
    void extractRoomIdWithAdditionalPath() {
        // given
        String destination = "/sub/api/v1/chat/1/member";

        // when
        Long roomId = chatDestinationResolver.extractRoomId(destination);

        // then
        assertThat(roomId).isNull();
    }

    @Test
    @DisplayName("숫자가 아닌 채팅방 id일 경우, null 반환")
    void extractRoomIdWithInvalidRoomId() {
        // given
        String destination = "/sub/api/v1/chat/abc";

        // when
        Long roomId = chatDestinationResolver.extractRoomId(destination);

        // then
        assertThat(roomId).isNull();
    }
}