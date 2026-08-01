package com.planb.unit.query.chat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.planb.query.chat.repository.ChatMessageQueryRepository;
import com.planb.query.chat.service.ChatMessageQueryService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageQueryServiceTest {

    @Mock
    private ChatMessageQueryRepository chatMessageQueryRepository;

    @InjectMocks
    private ChatMessageQueryService chatMessageQueryService;

    @Test
    @DisplayName("채팅방의 모든 메시지를 Soft Delete 한다")
    void softDeleteAllMessageInChatRoom() {

        // given
        Long roomId = 1L;
        Long deletedCount = 3L;

        when(chatMessageQueryRepository
                .softDeleteAllMessageInChatRoom(roomId))
                .thenReturn(deletedCount);

        // when
        Long result = chatMessageQueryService
                .softDeleteAllMessageInChatRoom(roomId);

        // then
        assertThat(result)
                .isEqualTo(deletedCount);

        verify(chatMessageQueryRepository)
                .softDeleteAllMessageInChatRoom(roomId);
    }
}