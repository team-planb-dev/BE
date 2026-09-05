package com.planb.controller.domain.chat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.chat.controller.ChatController;
import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.facade.ChatFacade;
import com.planb.domain.travel.dto.request.EditPlanRequest;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.facade.TravelFacade;

import java.security.Principal;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatControllerTest {

    @Mock
    private ChatFacade chatFacade;

    @Mock
    private TravelFacade travelFacade;

    private ChatController chatController;

    @BeforeEach
    void setUp() {
        chatController = new ChatController(chatFacade, travelFacade);
    }

    @Test
    @DisplayName("Travel과 연결된 방이면 편집 미리보기를 생성해 AI 응답 발행에 위임한다")
    void sendMessageWithTravelLink() {

        // given
        Long roomId = 1L;
        Long travelId = 100L;

        SendChatMessageRequest request =
                new SendChatMessageRequest(MessageType.TALK, "안녕하세요.");

        Principal principal = () -> "testUser@example.com";

        EditPlanAiResponse editPlanAiResponse =
                new EditPlanAiResponse(
                        "부산 여행",
                        List.of(),
                        List.of("변경 사항 없음"),
                        true
                );

        EditPlanPreviewResponse preview =
                new EditPlanPreviewResponse(
                        null,
                        editPlanAiResponse
                );

        when(chatFacade.findTravelIdByRoomId(roomId))
                .thenReturn(Optional.of(travelId));

        when(travelFacade.makeEditPlanPreview(
                new EditPlanRequest(travelId, request.message()),
                "testUser@example.com"
        ))
                .thenReturn(preview);

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
                        "testUser@example.com");

        verify(chatFacade)
                .publishTalkReply(roomId, preview);
    }

    @Test
    @DisplayName("Travel과 연결되지 않은 순수 채팅방이면 AI 미리보기 없이 메시지만 전달한다")
    void sendMessageWithoutTravelLink() {

        // given
        Long roomId = 1L;

        SendChatMessageRequest request =
                new SendChatMessageRequest(MessageType.TALK, "안녕하세요.");

        Principal principal = () -> "testUser@example.com";

        when(chatFacade.findTravelIdByRoomId(roomId))
                .thenReturn(Optional.empty());

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
                        "testUser@example.com");

        verifyNoInteractions(travelFacade);

        verify(chatFacade, never())
                .publishTalkReply(any(), any());
    }
}
