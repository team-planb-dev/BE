package com.planb.domain.chat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.facade.ChatMessageFacade;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@MessageMapping("/api/v1/chat")
public class ChatController {

    private final ChatMessageFacade chatMessageFacade;

    @MessageMapping("/{roomId}/send")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Payload SendChatMessageRequest request,
            Principal principal
    ) {

        chatMessageFacade.handleMessage(
                roomId,
                request,
                principal.getName()
        );
    }
}
