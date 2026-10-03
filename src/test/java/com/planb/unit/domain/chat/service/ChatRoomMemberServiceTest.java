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
import com.planb.query.chat.service.ChatRoomMemberQueryService;
import com.planb.domain.travel.entity.Travel;
import com.planb.global.config.exception.domain.ForbiddenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatRoomMemberServiceTest {

    @Mock
    private ChatRoomMemberRepository chatRoomMemberRepository;

    @Mock
    private ChatRoomMemberQueryService chatRoomMemberQueryService;

    @InjectMocks
    private ChatRoomMemberService chatRoomMemberService;

    @Test
    @DisplayName("채팅방에 사용자를 추가 후, 입장 응답을 반환")
    void addChatUser() {

        // given
        Long chatRoomId = 1L;
        String username = "woojuice@example.com";

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
                .isEqualTo("woojuice@example.com님이 채팅방에 입장하셨습니다.");
    }

    @Test
    @DisplayName("채팅방 사용자를 삭제 후, 퇴장 응답을 반환")
    void deleteChatUser() {

        // given
        Long chatRoomId = 1L;
        String username = "woojuice@example.com";

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
                .isEqualTo("woojuice@example.com님이 퇴장하셨습니다.");
    }

    @Test
    @DisplayName("여행 채팅방 기존 구성원의 중복 등록 생략")
    void ensureExistingMember() {

        ChatRoom room = ChatRoom.builder().id(1L).build();
        User user = User.builder().id(2L).build();
        when(chatRoomMemberQueryService.checkSubscriberWithRoomId(1L, 2L))
                .thenReturn(true);

        chatRoomMemberService.ensureMember(room, user);

        verify(chatRoomMemberRepository, never())
                .save(any());
    }

    @Test
    @DisplayName("여행 채팅방 신규 구성원 등록")
    void ensureNewMember() {

        ChatRoom room = ChatRoom.builder().id(1L).build();
        User user = User.builder().id(2L).username("user@example.com").build();
        when(chatRoomMemberQueryService.checkSubscriberWithRoomId(1L, 2L))
                .thenReturn(false);

        chatRoomMemberService.ensureMember(room, user);

        verify(chatRoomMemberRepository)
                .save(any(ChatRoomMember.class));
    }

    @Test
    @DisplayName("인증 사용자와 다른 멤버십 변경 거부")
    void rejectOtherUserMembershipChange() {

        User user = User.builder().id(2L).build();

        assertThatThrownBy(() -> chatRoomMemberService.validateRequestedUser(3L, user))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    @DisplayName("여행 채팅방의 비소유자 변경 거부")
    void rejectOtherTravelOwner() {

        User owner = User.builder().id(1L).build();
        User participant = User.builder().id(2L).build();
        Travel travel = Travel.builder().user(owner).build();
        ChatRoom room = ChatRoom.builder().travel(travel).build();

        assertThatThrownBy(() -> chatRoomMemberService.validateTravelRoomOwner(room, participant))
                .isInstanceOf(ForbiddenException.class);
    }
}
