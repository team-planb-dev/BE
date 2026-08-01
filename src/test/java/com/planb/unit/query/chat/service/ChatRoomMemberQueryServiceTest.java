package com.planb.unit.query.chat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.planb.domain.chat.entity.ChatRoomMember;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.query.chat.repository.ChatRoomMemberQueryRepository;
import com.planb.query.chat.service.ChatRoomMemberQueryService;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRoomMemberQueryServiceTest {

    @Mock
    private ChatRoomMemberQueryRepository chatRoomMemberQueryRepository;

    @InjectMocks
    private ChatRoomMemberQueryService chatRoomMemberQueryService;

    @Test
    @DisplayName("채팅방 id를 통한 채팅방 멤버 조회")
    void findByRoomId() {

        // given
        Long roomId = 1L;

        ChatRoomMember firstMember = mock(ChatRoomMember.class);

        ChatRoomMember secondMember = mock(ChatRoomMember.class);

        List<ChatRoomMember> chatRoomMembers = List.of(
                firstMember,
                secondMember
        );

        when(chatRoomMemberQueryRepository
                .findByRoomId(roomId))
                .thenReturn(chatRoomMembers);

        // when
        List<ChatRoomMember> result = chatRoomMemberQueryService
                .findByRoomId(roomId);

        // then
        assertThat(result)
                .isSameAs(chatRoomMembers);

        assertThat(result)
                .hasSize(2)
                .containsExactly(
                        firstMember,
                        secondMember
                );

        verify(chatRoomMemberQueryRepository)
                .findByRoomId(roomId);
    }

    @Test
    @DisplayName("채팅방 id와 사용자 id를 통해 채팅방 멤버 조회 성공")
    void findByUserIdSuccess() {

        // given
        Long roomId = 1L;
        Long userId = 10L;

        ChatRoomMember chatRoomMember = mock(ChatRoomMember.class);

        when(chatRoomMemberQueryRepository
                .findByUserIdWithRoomId(roomId, userId))
                .thenReturn(Optional.of(chatRoomMember));

        // when
        ChatRoomMember result = chatRoomMemberQueryService
                .findByUserId(roomId, userId);

        // then
        assertThat(result)
                .isSameAs(chatRoomMember);

        verify(chatRoomMemberQueryRepository)
                .findByUserIdWithRoomId(roomId, userId);
    }

    @Test
    @DisplayName("채팅방 멤버가 존재하지 않으면 예외 발생")
    void findByUserIdFail() {

        // given
        Long roomId = 1L;
        Long userId = 10L;

        when(chatRoomMemberQueryRepository
                .findByUserIdWithRoomId(roomId, userId))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() ->
                chatRoomMemberQueryService
                        .findByUserId(roomId, userId))
                .isInstanceOf(BaseException.class);

        verify(chatRoomMemberQueryRepository)
                .findByUserIdWithRoomId(roomId, userId);
    }

    @Test
    @DisplayName("사용자가 채팅방 구독자인 경우, true 반환")
    void checkSubscriberWithRoomIdSuccess() {

        // given
        Long roomId = 1L;
        Long userId = 10L;

        when(chatRoomMemberQueryRepository
                .checkSubscriberWithRoomId(roomId, userId))
                .thenReturn(true);

        // when
        boolean result = chatRoomMemberQueryService
                .checkSubscriberWithRoomId(roomId, userId);

        // then
        assertThat(result)
                .isTrue();

        verify(chatRoomMemberQueryRepository)
                .checkSubscriberWithRoomId(roomId, userId);
    }

    @Test
    @DisplayName("사용자가 채팅방 구독자가 아닌 경우, false 반환")
    void checkSubscriberWithRoomIdFail() {

        // given
        Long roomId = 1L;
        Long userId = 10L;

        when(chatRoomMemberQueryRepository
                .checkSubscriberWithRoomId(roomId, userId))
                .thenReturn(false);

        // when
        boolean result = chatRoomMemberQueryService
                .checkSubscriberWithRoomId(roomId, userId);

        // then
        assertThat(result)
                .isFalse();

        verify(chatRoomMemberQueryRepository)
                .checkSubscriberWithRoomId(roomId, userId);
    }

    @Test
    @DisplayName("중복 참여자가 아니면 검증 통과")
    void validateDuplicateMemberWithRoomSuccess() {

        // given
        Long roomId = 1L;
        Long userId = 10L;

        when(chatRoomMemberQueryRepository
                .checkSubscriberWithRoomId(roomId, userId))
                .thenReturn(false);

        // when & then
        assertThatCode(() ->
                chatRoomMemberQueryService
                        .validateDuplicateMemberWithRoom(
                                roomId,
                                userId
                        ))
                .doesNotThrowAnyException();

        verify(chatRoomMemberQueryRepository)
                .checkSubscriberWithRoomId(roomId, userId);
    }

    @Test
    @DisplayName("이미 참여한 사용자는 중복 예외 발생")
    void validateDuplicateMemberWithRoomFail() {

        // given
        Long roomId = 1L;
        Long userId = 10L;

        when(chatRoomMemberQueryRepository
                .checkSubscriberWithRoomId(roomId, userId))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() ->
                chatRoomMemberQueryService
                        .validateDuplicateMemberWithRoom(
                                roomId,
                                userId
                        ))
                .isInstanceOf(BaseException.class);

        verify(chatRoomMemberQueryRepository)
                .checkSubscriberWithRoomId(roomId, userId);
    }

    @Test
    @DisplayName("채팅방 id를 통한 채팅방 멤버 전체 삭제")
    void deleteAllChatRoomMemberByRoomId() {

        // given
        Long roomId = 1L;

        // when
        chatRoomMemberQueryService
                .deleteAllChatRoomMemberByRoomId(roomId);

        // then
        verify(chatRoomMemberQueryRepository)
                .deleteAllChatMemberByRoomId(roomId);
    }
}