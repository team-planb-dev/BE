package com.planb.unit.domain.chat.stompController;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.planb.domain.chat.controller.ChatController;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.facade.ChatFacade;

import java.security.Principal;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatFacade chatFacade;

    private ChatController chatController;

    @BeforeEach
    void setUp() {
        chatController = new ChatController(chatFacade);
    }

    @Test
    @DisplayName("채팅 메시지 전송 요청을 ChatFacade에 위임한다")
    void sendMessageSuccess() {

        // given
        Long roomId = 1L;

        SendChatMessageRequest request =
                new SendChatMessageRequest("안녕하세요.");

        Principal principal = () -> "testUser";

        // when
        chatController.sendMessage(
                roomId,
                request,
                principal);

        // then
        verify(chatFacade)
                .publishMessage(
                        roomId,
                        request,
                        "testUser");
    }
}