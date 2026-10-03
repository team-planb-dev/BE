package com.planb.unit.domain.chat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.dto.response.SendChatMessageResponse;
import com.planb.domain.chat.entity.ChatMessage;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.chat.repository.ChatMessageRepository;
import com.planb.domain.chat.service.ChatMessageService;
import com.planb.domain.chat.dto.response.AiReplyContent;
import com.planb.domain.chat.helper.ChatAiReplyMessageHelper;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.constant.SystemAccountConstants;
import com.planb.query.chat.service.ChatRoomQueryService;
import com.planb.query.user.service.UserQueryService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ChatAiReplyMessageHelper chatAiReplyMessageHelper;

    @Mock
    private ChatRoomQueryService chatRoomQueryService;

    @Mock
    private UserQueryService userQueryService;

    @InjectMocks
    private ChatMessageService chatMessageService;

    @Test
    @DisplayName("빈 TALK 메시지 거부")
    void rejectBlankTalkMessage() {

        SendChatMessageRequest request = new SendChatMessageRequest(
                MessageType.TALK,
                "  "
        );

        assertThatThrownBy(() -> chatMessageService.validateRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("TALK 메시지 내용은 필수입니다.");
    }

    @Test
    @DisplayName("메시지 타입 누락 거부")
    void rejectMissingMessageType() {

        SendChatMessageRequest request = new SendChatMessageRequest(null, "내용");

        assertThatThrownBy(() -> chatMessageService.validateRequest(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메시지 타입은 필수입니다.");
    }

    @Test
    @DisplayName("사용자 메시지 저장 및 구독 채널 발행")
    void publishUserMessage() {

        ChatRoom room = ChatRoom.builder().id(1L).build();
        User sender = User.builder().id(2L).username("user@example.com").nickname("우주").build();
        when(chatRoomQueryService.findChatRoomByRoomId(1L)).thenReturn(room);
        when(userQueryService.findByUsername("user@example.com")).thenReturn(sender);

        chatMessageService.publishUserMessage(1L, "안녕하세요", "user@example.com");

        org.mockito.ArgumentCaptor<ChatMessage> saved =
                org.mockito.ArgumentCaptor.forClass(ChatMessage.class);
        org.mockito.ArgumentCaptor<SendChatMessageResponse> published =
                org.mockito.ArgumentCaptor.forClass(SendChatMessageResponse.class);

        verify(chatMessageRepository).save(saved.capture());
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/sub/api/v1/chat/1"),
                published.capture()
        );

        assertThat(saved.getValue().getChatRoom()).isSameAs(room);
        assertThat(saved.getValue().getSender()).isSameAs(sender);
        assertThat(saved.getValue().getMessage()).isEqualTo("안녕하세요");
        assertThat(published.getValue().message()).isEqualTo("안녕하세요");
        assertThat(published.getValue().type()).isEqualTo(MessageType.TALK);
    }

    @Test
    @DisplayName("AI 응답 저장 및 구독 채널 발행")
    void publishAiReply() {

        ChatRoom room = ChatRoom.builder().id(1L).build();
        User ai = User.builder().id(3L).username(SystemAccountConstants.AI_BOT_USERNAME).nickname("AI").build();
        when(chatRoomQueryService.findChatRoomByRoomId(1L)).thenReturn(room);
        when(userQueryService.findByUsername(SystemAccountConstants.AI_BOT_USERNAME)).thenReturn(ai);

        chatMessageService.publishAiReply(1L, "수정안", null, MessageType.TALK);

        org.mockito.ArgumentCaptor<SendChatMessageResponse> published =
                org.mockito.ArgumentCaptor.forClass(SendChatMessageResponse.class);

        verify(chatMessageRepository).save(any(ChatMessage.class));
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/sub/api/v1/chat/1"),
                published.capture()
        );
        assertThat(published.getValue().senderId()).isEqualTo(3L);
        assertThat(published.getValue().message()).isEqualTo("수정안");
    }

    @Test
    @DisplayName("여행 채팅방 기존 메시지 존재 시 인사 생략")
    void skipGreetingForExistingMessages() {

        when(chatRoomQueryService.findTravelIdByRoomId(1L))
                .thenReturn(Optional.of(10L));
        when(chatMessageRepository.existsByChatRoom_IdAndDeletedFalse(1L))
                .thenReturn(true);

        chatMessageService.publishAiGreetingIfNeeded(1L, "user@example.com");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("일반 채팅방 AI 인사 생략")
    void skipGreetingForNonTravelRoom() {

        when(chatRoomQueryService.findTravelIdByRoomId(1L))
                .thenReturn(Optional.empty());

        chatMessageService.publishAiGreetingIfNeeded(1L, "user@example.com");

        verifyNoInteractions(messagingTemplate);
    }

    @Test
    @DisplayName("첫 여행 채팅방 입장 시 AI 인사 두 건 발행")
    void publishGreetingForNewTravelRoom() {

        ChatRoom room = ChatRoom.builder().id(1L).build();
        User participant = User.builder().id(2L).username("user@example.com").nickname("우주").build();
        User ai = User.builder().id(3L).username(SystemAccountConstants.AI_BOT_USERNAME).nickname("AI").build();

        when(chatRoomQueryService.findTravelIdByRoomId(1L))
                .thenReturn(Optional.of(10L));
        when(userQueryService.findByUsername("user@example.com"))
                .thenReturn(participant);
        when(userQueryService.findByUsername(SystemAccountConstants.AI_BOT_USERNAME))
                .thenReturn(ai);
        when(chatAiReplyMessageHelper.makeGreetingMessages("우주", "AI"))
                .thenReturn(List.of("인사1", "인사2"));
        when(chatRoomQueryService.findChatRoomByRoomId(1L))
                .thenReturn(room);

        chatMessageService.publishAiGreetingIfNeeded(1L, "user@example.com");

        org.mockito.ArgumentCaptor<ChatMessage> saved =
                org.mockito.ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, org.mockito.Mockito.times(2))
                .save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(ChatMessage::getMessage)
                .containsExactly("인사1", "인사2");
    }

    @Test
    @DisplayName("시스템 메시지 DB 저장 없이 구독 채널 발행")
    void publishSystemMessageWithoutSave() {

        User participant = User.builder().id(2L).username("user@example.com").nickname("우주").build();
        when(userQueryService.findByUsername("user@example.com"))
                .thenReturn(participant);
        chatMessageService.publishSystemMessage(1L, "user@example.com", MessageType.ENTER);

        org.mockito.ArgumentCaptor<SendChatMessageResponse> published =
                org.mockito.ArgumentCaptor.forClass(SendChatMessageResponse.class);
        verify(messagingTemplate).convertAndSend(
                org.mockito.ArgumentMatchers.eq("/sub/api/v1/chat/1"),
                published.capture()
        );
        verify(chatMessageRepository, never()).save(any(ChatMessage.class));
        assertThat(published.getValue().type()).isEqualTo(MessageType.ENTER);
        assertThat(published.getValue().senderId()).isEqualTo(2L);
        assertThat(published.getValue().message()).isEqualTo("우주님이 입장했습니다.");
    }

    @Test
    @DisplayName("채팅 메시지를 구독 채널로 발행")
    void publishMessage() {

        // given
        Long roomId = 1L;

        SendChatMessageResponse response =
                new SendChatMessageResponse(
                        MessageType.TALK,
                        roomId,
                        10L,
                        "우주",
                        "테스트 메시지",
                        null,
                        Instant.now()
                );

        // when
        chatMessageService.publishMessage(
                roomId,
                response
        );

        // then
        verify(messagingTemplate)
                .convertAndSend(
                        "/sub/api/v1/chat/" + roomId,
                        response
                );
    }

    @Test
    @DisplayName("채팅 메시지 객체를 생성")
    void createChatMessage() {

        // given
        ChatRoom chatRoom = mock(ChatRoom.class);
        User sender = mock(User.class);
        String message = "테스트 메시지";

        Instant beforeCreate = Instant.now();

        // when
        ChatMessage result =
                chatMessageService.createChatMessage(
                        chatRoom,
                        sender,
                        message
                );

        Instant afterCreate = Instant.now();

        // then
        assertThat(result.getChatRoom())
                .isSameAs(chatRoom);

        assertThat(result.getSender())
                .isSameAs(sender);

        assertThat(result.getMessage())
                .isEqualTo(message);

        assertThat(result.getSendAt())
                .isBetween(
                        beforeCreate,
                        afterCreate
                );

        assertThat(result.isDeleted())
                .isFalse();
    }

    @Test
    @DisplayName("채팅 메시지 응답 DTO를 생성")
    void makeChatResponse() {

        // given
        Long roomId = 1L;
        Long senderId = 10L;
        String senderNickname = "우주";
        String message = "테스트 메시지";

        Instant sendAt =
                Instant.parse(
                        "2026-07-27T10:00:00Z"
                );

        User sender = mock(User.class);
        ChatMessage chatMessage = mock(ChatMessage.class);

        when(sender.getId())
                .thenReturn(senderId);

        when(sender.getNickname())
                .thenReturn(senderNickname);

        when(chatMessage.getMessage())
                .thenReturn(message);

        when(chatMessage.getSendAt())
                .thenReturn(sendAt);

        // when
        SendChatMessageResponse result =
                chatMessageService.makeChatResponse(
                        roomId,
                        sender,
                        chatMessage
                );

        // then
        assertThat(result.type())
                .isEqualTo(MessageType.TALK);

        assertThat(result.roomId())
                .isEqualTo(roomId);

        assertThat(result.senderId())
                .isEqualTo(senderId);

        assertThat(result.senderNickname())
                .isEqualTo(senderNickname);

        assertThat(result.message())
                .isEqualTo(message);

        assertThat(result.sendTime())
                .isEqualTo(sendAt);
    }

    @Test
    @DisplayName("채팅 메시지를 저장")
    void saveMessage() {

        // given
        ChatMessage chatMessage =
                mock(ChatMessage.class);

        // when
        chatMessageService.saveMessage(
                chatMessage
        );

        // then
        verify(chatMessageRepository)
                .save(chatMessage);
    }

    @Test
    @DisplayName("입장 시스템 메시지를 생성")
    void createSystemMessageEnter() {

        // given
        String nickname = "우주";

        // when
        String result =
                chatMessageService.createSystemMessage(
                        MessageType.ENTER,
                        nickname
                );

        // then
        assertThat(result)
                .isEqualTo(
                        "우주님이 입장했습니다."
                );
    }

    @Test
    @DisplayName("퇴장 시스템 메시지 생성")
    void createSystemMessageLeave() {

        // given
        String nickname = "우주";

        // when
        String result =
                chatMessageService.createSystemMessage(
                        MessageType.LEAVE,
                        nickname
                );

        // then
        assertThat(result)
                .isEqualTo(
                        "우주님이 퇴장했습니다."
                );
    }

    @Test
    @DisplayName("지원하지 않는 타입으로 시스템 메시지 생성 시, 예외가 발생")
    void createSystemMessageUnsupportedType() {

        // when & then
        assertThatThrownBy(() ->
                chatMessageService.createSystemMessage(
                        MessageType.TALK,
                        "우주"
                )
        )
                .isInstanceOf(
                        IllegalArgumentException.class
                )
                .hasMessage(
                        "지원하지 않는 시스템 메시지 타입입니다."
                );
    }

    @Test
    @DisplayName("처리 가능한 수정 요청이면 미리보기를 포함한 응답 컨텐츠 반환")
    void resolveAiReplyContentWhenProcessable() {

        // given
        EditPlanAiResponse editPlanAiResponse =
                new EditPlanAiResponse(
                        "부산 여행",
                        List.of(),
                        List.of("변경 사항 없음"),
                        true
                );

        EditPlanPreviewResponse preview =
                new EditPlanPreviewResponse(null, editPlanAiResponse);

        when(chatAiReplyMessageHelper.makeReplyMessage(preview))
                .thenReturn("변경 사항 없음");

        // when
        AiReplyContent result =
                chatMessageService.resolveAiReplyContent(preview);

        // then
        assertThat(result.message())
                .isEqualTo("변경 사항 없음");

        assertThat(result.editPreview())
                .isSameAs(preview);
    }

    @Test
    @DisplayName("처리 불가능한 요청이면 미리보기 없이 거절 메시지만 반환")
    void resolveAiReplyContentWhenNotProcessable() {

        // given
        EditPlanAiResponse editPlanAiResponse =
                new EditPlanAiResponse(
                        "부산 여행",
                        List.of(),
                        List.of(),
                        false
                );

        EditPlanPreviewResponse preview =
                new EditPlanPreviewResponse(null, editPlanAiResponse);

        when(chatAiReplyMessageHelper.makeReplyMessage(preview))
                .thenReturn("해당 요청은 처리하기 어렵습니다! 다른 요청 부탁드려요.");

        // when
        AiReplyContent result =
                chatMessageService.resolveAiReplyContent(preview);

        // then
        assertThat(result.message())
                .isEqualTo("해당 요청은 처리하기 어렵습니다! 다른 요청 부탁드려요.");

        assertThat(result.editPreview())
                .isNull();
    }

    @Test
    @DisplayName("채팅방에 메시지가 존재하면 true 반환")
    void existsAnyMessageReturnsTrueWhenMessageExists() {

        // given
        Long roomId = 1L;

        when(chatMessageRepository.existsByChatRoom_IdAndDeletedFalse(roomId))
                .thenReturn(true);

        // when
        boolean result = chatMessageService.existsAnyMessage(roomId);

        // then
        assertThat(result)
                .isTrue();
    }

    @Test
    @DisplayName("채팅방에 메시지가 없으면 false 반환")
    void existsAnyMessageReturnsFalseWhenNoMessage() {

        // given
        Long roomId = 1L;

        when(chatMessageRepository.existsByChatRoom_IdAndDeletedFalse(roomId))
                .thenReturn(false);

        // when
        boolean result = chatMessageService.existsAnyMessage(roomId);

        // then
        assertThat(result)
                .isFalse();
    }

    @Test
    @DisplayName("CONFIRM 완료 메시지를 Helper에 위임해 조회")
    void resolveConfirmMessage() {

        // given
        when(chatAiReplyMessageHelper.makeConfirmMessage())
                .thenReturn("일정을 저장했어요!");

        // when
        String result = chatMessageService.resolveConfirmMessage();

        // then
        assertThat(result)
                .isEqualTo("일정을 저장했어요!");
    }

    @Test
    @DisplayName("CANCEL 완료 메시지를 Helper에 위임해 조회")
    void resolveCancelMessage() {

        // given
        when(chatAiReplyMessageHelper.makeCancelMessage())
                .thenReturn("기존 일정을 유지했어요!");

        // when
        String result = chatMessageService.resolveCancelMessage();

        // then
        assertThat(result)
                .isEqualTo("기존 일정을 유지했어요!");
    }

    @Test
    @DisplayName("인사 메시지 목록을 Helper에 위임해 조회")
    void resolveGreetingMessages() {

        // given
        String userNickname = "우주";
        String aiNickname = "AI 비서";

        List<String> greetingMessages =
                List.of("인사1", "인사2");

        when(chatAiReplyMessageHelper.makeGreetingMessages(userNickname, aiNickname))
                .thenReturn(greetingMessages);

        // when
        List<String> result =
                chatMessageService.resolveGreetingMessages(userNickname, aiNickname);

        // then
        assertThat(result)
                .isEqualTo(greetingMessages);
    }
}
