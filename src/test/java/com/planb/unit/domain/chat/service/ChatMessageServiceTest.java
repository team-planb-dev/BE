package com.planb.unit.domain.chat.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.response.SendChatMessageResponse;
import com.planb.domain.chat.entity.ChatMessage;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.chat.repository.ChatMessageRepository;
import com.planb.domain.chat.service.ChatMessageService;
import com.planb.domain.user.entity.User;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageServiceTest {

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ChatMessageService chatMessageService;

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
    @DisplayName("퇴장 시스템 메시지를 생성한다")
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
}