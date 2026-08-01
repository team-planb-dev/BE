package com.planb.unit.domain.chat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.planb.domain.chat.dto.request.AddChatUserRequest;
import com.planb.domain.chat.dto.request.DeleteChatUserRequest;
import com.planb.domain.chat.dto.response.AddChatUserResponse;
import com.planb.domain.chat.dto.response.DeleteChatUserResponse;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.chat.entity.ChatRoomMember;
import com.planb.domain.chat.repository.ChatRoomMemberRepository;
import com.planb.domain.chat.service.ChatRoomMemberService;
import com.planb.domain.user.entity.User;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomMemberServiceTest {

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @InjectMocks
    private ChatRoomMemberService chatRoomMemberService;

    @Test
    @DisplayName("채팅방에 사용자를 추가 후, 입장 응답을 반환")
    void addChatUser() {

        // given
        Long chatRoomId = 1L;
        String username = "woojuice";

        ChatRoom chatRoom = mock(ChatRoom.class);
        User user = mock(User.class);

        when(chatRoom.getId())
                .thenReturn(chatRoomId);

        when(user.getUsername())
                .thenReturn(username);

        AddChatUserRequest request =
                new AddChatUserRequest(
                        chatRoom,
                        user
                );

        ArgumentCaptor<ChatRoomMember> chatRoomMemberCaptor =
                ArgumentCaptor.forClass(ChatRoomMember.class);

        // when
        AddChatUserResponse result =
                chatRoomMemberService.addChatUser(request);

        // then
        verify(chatRoomMemberRepository)
                .save(chatRoomMemberCaptor.capture());

        ChatRoomMember savedChatRoomMember =
                chatRoomMemberCaptor.getValue();

        assertThat(savedChatRoomMember.getChatRoom())
                .isEqualTo(chatRoom);

        assertThat(savedChatRoomMember.getUser())
                .isEqualTo(user);

        assertThat(result.username())
                .isEqualTo(username);

        assertThat(result.chatRoomId())
                .isEqualTo(chatRoomId);

        assertThat(result.message())
                .isEqualTo("woojuice님이 채팅방에 입장하셨습니다.");
    }

    @Test
    @DisplayName("채팅방 사용자를 삭제 후, 퇴장 응답을 반환")
    void deleteChatUser() {

        // given
        Long chatRoomId = 1L;
        String username = "woojuice";

        ChatRoomMember chatRoomMember =
                mock(ChatRoomMember.class);

        ChatRoom chatRoom =
                mock(ChatRoom.class);

        User user =
                mock(User.class);

        when(chatRoom.getId())
                .thenReturn(chatRoomId);

        when(user.getUsername())
                .thenReturn(username);

        DeleteChatUserRequest request =
                new DeleteChatUserRequest(
                        chatRoomMember,
                        chatRoom,
                        user
                );

        // when
        DeleteChatUserResponse result =
                chatRoomMemberService.deleteChatUser(request);

        // then
        verify(chatRoomMemberRepository)
                .delete(chatRoomMember);

        assertThat(result.chatRoomId())
                .isEqualTo(chatRoomId);

        assertThat(result.username())
                .isEqualTo(username);

        assertThat(result.message())
                .isEqualTo("woojuice님이 퇴장하셨습니다.");
    }
}