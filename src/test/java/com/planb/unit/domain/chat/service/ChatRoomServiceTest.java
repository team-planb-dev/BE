package com.planb.unit.domain.chat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;
import com.planb.domain.chat.dto.request.CreateChatRoomRequest;
import com.planb.domain.chat.dto.response.CreateChatRoomResponse;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.chat.repository.ChatRoomRepository;
import com.planb.domain.chat.service.ChatRoomService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomServiceTest {

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @InjectMocks
    private ChatRoomService chatRoomService;

    @Test
    @DisplayName("채팅방을 삭제 상태로 변경")
    void deleteChatRoom() {

        // given
        ChatRoom chatRoom = mock(ChatRoom.class);

        // when
        chatRoomService.deleteChatRoom(chatRoom);

        // then
        verify(chatRoom).delete();
    }

    @Test
    @DisplayName("채팅방을 생성하고 생성 응답을 반환")
    void createChatRoom() {

        // given
        CreateChatRoomRequest request =
                new CreateChatRoomRequest(
                        "테스트 채팅방"
                );

        ArgumentCaptor<ChatRoom> chatRoomCaptor =
                ArgumentCaptor.forClass(ChatRoom.class);

        when(chatRoomRepository.save(any(ChatRoom.class)))
                .thenAnswer(invocation -> {

                    ChatRoom chatRoom = invocation.getArgument(0);

                    ReflectionTestUtils
                            .setField(
                                    chatRoom,
                                    "id",
                                    1L);
                    return chatRoom;
                });

        // when
        CreateChatRoomResponse result =
                chatRoomService.createChatRoom(request);

        // then
        verify(chatRoomRepository)
                .save(chatRoomCaptor.capture());

        ChatRoom savedChatRoom =
                chatRoomCaptor.getValue();

        assertThat(savedChatRoom
                .getChatRoomName())
                .isEqualTo("테스트 채팅방");

        assertThat(savedChatRoom
                .isDeleted())
                .isFalse();

        assertThat(result
                .chatRoomId())
                .isEqualTo(1L);

        assertThat(result
                .chatRoomName())
                .isEqualTo("테스트 채팅방");

        assertThat(result
                .createdAt())
                .isEqualTo(savedChatRoom.getCreatedAt());

        assertThat(result
                .message())
                .isEqualTo("채팅방이 생성되었습니다.");
    }
}