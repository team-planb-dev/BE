package com.planb.domain.chat.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.facade.ChatFacade;
import com.planb.domain.chat.facade.ChatMessageFacade;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
@MessageMapping("/api/v1/chat")
public class ChatController {

    private final ChatMessageFacade chatMessageFacade;
    private final ChatFacade chatFacade;

    @MessageMapping("/{roomId}/send")
    public void sendMessage(
            @DestinationVariable Long roomId,
            @Payload SendChatMessageRequest request,
            Principal principal
    ) {

        try {
            chatMessageFacade.handleMessage(
                    roomId,
                    request,
                    principal.getName()
            );

        } catch (Exception e) {

            // STOMP는 요청에 대응하는 응답 자리가 없어 예외가 클라이언트에 닿지 않는다.
            // 알리지 않으면 사용자에게는 침묵으로만 보인다. 사유는 로그에만 남긴다.
            log.error(
                    "채팅 메시지 처리 실패 - roomId: {}, username: {}",
                    roomId,
                    principal.getName(),
                    e
            );

            chatFacade.publishEditFailedReply(roomId);
        }
    }
}
