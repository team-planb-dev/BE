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

            // STOMP 요청의 직접 응답 부재에 따른 예외 전달 불가
            // 사용자 침묵 방지를 위한 오류 안내, 상세 사유는 로그에만 기록
            log.error(
                    "채팅 메시지 처리 실패 - roomId: {}, username: {}",
                    roomId,
                    principal.getName(),
                    e
            );

            chatFacade.publishEditFailedReply(
                    roomId,
                    e
            );
        }
    }
}
