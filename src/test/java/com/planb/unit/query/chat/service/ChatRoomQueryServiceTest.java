package com.planb.unit.query.chat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.planb.domain.chat.entity.ChatRoom;
import com.planb.global.config.exception.WebSocketExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.query.chat.repository.ChatRoomQueryRepository;
import com.planb.query.chat.service.ChatRoomQueryService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomQueryServiceTest {

    @Mock
    private ChatRoomQueryRepository chatRoomQueryRepository;

    @InjectMocks
    private ChatRoomQueryService chatRoomQueryService;

    @Test
    @DisplayName("채팅방 id로 채팅방 조회")
    void findChatRoomByRoomIdSuccess() {

        // given
        Long roomId = 1L;

        ChatRoom chatRoom = mock(ChatRoom.class);

        when(chatRoomQueryRepository
                .findByRoomId(roomId))
                .thenReturn(Optional.of(chatRoom));

        // when
        ChatRoom result = chatRoomQueryService
                .findChatRoomByRoomId(roomId);

        // then
        assertThat(result)
                .isSameAs(chatRoom);

        verify(chatRoomQueryRepository)
                .findByRoomId(roomId);
    }

    @Test
    @DisplayName("존재하지 않는 채팅방을 조회 시, 예외 발생")
    void findChatRoomByRoomIdNotFound() {

        // given
        Long roomId = 1L;

        when(chatRoomQueryRepository
                .findByRoomId(roomId))
                .thenReturn(Optional.empty());

        // when & then
        BaseException exception = assertThrows(
                BaseException.class,
                () -> chatRoomQueryService
                        .findChatRoomByRoomId(roomId)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(WebSocketExceptionEnum.CHATROOM_NOT_FOUND.getCode());

        assertThat(exception.getMessage())
                .isEqualTo(WebSocketExceptionEnum.CHATROOM_NOT_FOUND.getMessage());

        verify(chatRoomQueryRepository)
                .findByRoomId(roomId);
    }


    @Test
    @DisplayName("travelId로 연결된 채팅방 조회")
    void findChatRoomByTravelIdFound() {

        // given
        Long travelId = 1L;

        ChatRoom chatRoom = mock(ChatRoom.class);

        when(chatRoomQueryRepository
                .findByTravelId(travelId))
                .thenReturn(Optional.of(chatRoom));

        // when
        Optional<ChatRoom> result =
                chatRoomQueryService.findChatRoomByTravelId(travelId);

        // then
        assertThat(result)
                .contains(chatRoom);

        verify(chatRoomQueryRepository)
                .findByTravelId(travelId);
    }

    @Test
    @DisplayName("travelId에 연결된 채팅방이 없으면 빈 값 반환")
    void findChatRoomByTravelIdNotFound() {

        // given
        Long travelId = 1L;

        when(chatRoomQueryRepository
                .findByTravelId(travelId))
                .thenReturn(Optional.empty());

        // when
        Optional<ChatRoom> result =
                chatRoomQueryService.findChatRoomByTravelId(travelId);

        // then
        assertThat(result)
                .isEmpty();

        verify(chatRoomQueryRepository)
                .findByTravelId(travelId);
    }

}
