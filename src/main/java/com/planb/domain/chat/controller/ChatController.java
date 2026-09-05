package com.planb.domain.chat.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;
import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.facade.ChatFacade;
import com.planb.domain.travel.dto.request.EditPlanRequest;
import com.planb.domain.travel.dto.request.GetAiPlanRequest;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.facade.TravelFacade;

import java.security.Principal;
import java.util.Optional;

@Controller
@RequiredArgsConstructor
@MessageMapping("/api/v1/chat")
public class ChatController {

    private final ChatFacade chatFacade;
    private final TravelFacade travelFacade;

    @MessageMapping("/{roomId}/send")
    public void sendMessage(@DestinationVariable Long roomId,
                            @Payload SendChatMessageRequest request,
                            Principal principal){

        String username = principal.getName();

        switch (request.type()) {
            case TALK -> handleTalk(roomId, request, username);
            case CONFIRM -> handleConfirm(roomId, username);
            case CANCEL -> handleCancel(roomId, username);
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 메시지 타입입니다."
            );
        }
    }

    private void handleTalk(Long roomId,
                            SendChatMessageRequest request,
                            String username){

        chatFacade.publishMessage(roomId, request, username);

        Optional<Long> travelId =
                chatFacade.findTravelIdByRoomId(roomId);

        if (travelId.isEmpty()) {
            return;
        }

        EditPlanPreviewResponse preview =
                travelFacade.makeEditPlanPreview(
                        new EditPlanRequest(travelId.get(), request.message()),
                        username
                );

        chatFacade.publishTalkReply(roomId, preview);
    }

    private void handleConfirm(Long roomId, String username){

        Long travelId = chatFacade.getTravelIdByRoomId(roomId);

        travelFacade.confirmEditPlan(
                new GetAiPlanRequest(travelId),
                username
        );

        chatFacade.publishAiReply(
                roomId,
                "수정된 일정을 저장했습니다.",
                null,
                MessageType.CONFIRM
        );
    }

    private void handleCancel(Long roomId, String username){

        Long travelId = chatFacade.getTravelIdByRoomId(roomId);

        travelFacade.cancelEditPlan(
                new GetAiPlanRequest(travelId),
                username
        );

        chatFacade.publishAiReply(
                roomId,
                "수정을 취소하고 기존 일정을 유지합니다.",
                null,
                MessageType.CANCEL
        );
    }
}
