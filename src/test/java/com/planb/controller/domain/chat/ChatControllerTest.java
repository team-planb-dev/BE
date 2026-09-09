package com.planb.controller.domain.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.planb.domain.chat.controller.ChatController;
import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.facade.ChatMessageFacade;

import java.security.Principal;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatMessageFacade chatMessageFacade;

    @InjectMocks
    private ChatController chatController;

    @Test
    @DisplayName("STOMP 메시지 처리를 인증 사용자와 함께 Facade에 위임")
    void sendMessageDelegatesToFacade() {

        // given
        Long roomId = 1L;
        String username = "testUser@example.com";

        SendChatMessageRequest request =
                new SendChatMessageRequest(
                        MessageType.TALK,
                        "일정을 수정해 주세요."
                );

        Principal principal = () -> username;

        // when
        chatController.sendMessage(
                roomId,
                request,
                principal
        );

        // then
        verify(chatMessageFacade)
                .handleMessage(
                        roomId,
                        request,
                        username
                );
    }
}
