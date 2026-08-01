package com.planb.domain.chat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.facade.ChatFacade;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@MessageMapping("/api/v1/chat")
public class ChatController {

    private final ChatFacade chatFacade;

    @MessageMapping("/{roomId}/send")
    public void sendMessage(@DestinationVariable Long roomId,
                            @Payload SendChatMessageRequest request,
                            Principal principal){

        chatFacade
                .publishMessage(
                        roomId,
                        request,
                        principal
                                .getName());

    }

    /**
    3-refactor-migrate-chat-presence-handling-to-stomp-session-events 변경점
    System 메시지를 수동 API가 아닌 EventListener를 통해 자동화
     */

    /*
    @MessageMapping("/{roomId}/enter")
    public void enter(@DestinationVariable Long roomId,
                      Principal principal){

        chatFacade.publishSystemMessage(
                roomId,
                principal
                        .getName(),
                MessageType.ENTER);
    }

    @MessageMapping("/{roomId}/leave")
    public void leave(@DestinationVariable Long roomId,
                      Principal principal){

        chatFacade.publishSystemMessage(
                roomId,
                principal
                        .getName(),
                MessageType.LEAVE);
    }

     */
}
