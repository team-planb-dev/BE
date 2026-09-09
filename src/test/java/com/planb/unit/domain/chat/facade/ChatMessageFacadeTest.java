package com.planb.unit.domain.chat.facade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.facade.ChatFacade;
import com.planb.domain.chat.facade.ChatMessageFacade;
import com.planb.domain.travel.dto.request.EditPlanRequest;
import com.planb.domain.travel.dto.request.GetAiPlanRequest;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.facade.TravelFacade;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageFacadeTest {

    @Mock
    private ChatFacade chatFacade;

    @Mock
    private TravelFacade travelFacade;

    @InjectMocks
    private ChatMessageFacade chatMessageFacade;

    @Test
    @DisplayName("TALK 메시지는 저장한 뒤 여행 수정 미리보기와 AI 응답을 발행")
    void handleTalkMessageWithTravel() {

        // given
        Long roomId = 1L;
        Long travelId = 100L;
        String username = "testUser@example.com";

        SendChatMessageRequest request =
                new SendChatMessageRequest(
                        MessageType.TALK,
                        "일정을 수정해 주세요."
                );

        EditPlanPreviewResponse preview =
                org.mockito.Mockito.mock(EditPlanPreviewResponse.class);

        when(chatFacade
                .findTravelIdByRoomId(roomId))
                .thenReturn(Optional.of(travelId));

        when(travelFacade
                .makeEditPlanPreview(
                        new EditPlanRequest(
                                travelId,
                                request.message()
                        ),
                        username
                ))
                .thenReturn(preview);

        // when
        chatMessageFacade.handleMessage(
                roomId,
                request,
                username
        );

        // then
        InOrder inOrder = inOrder(
                chatFacade,
                travelFacade
        );

        inOrder.verify(chatFacade)
                .publishMessage(
                        roomId,
                        request,
                        username
                );

        inOrder.verify(chatFacade)
                .findTravelIdByRoomId(roomId);

        inOrder.verify(travelFacade)
                .makeEditPlanPreview(
                        new EditPlanRequest(
                                travelId,
                                request.message()
                        ),
                        username
                );

        inOrder.verify(chatFacade)
                .publishTalkReply(
                        roomId,
                        preview
                );
    }

    @Test
    @DisplayName("여행과 연결되지 않은 TALK 메시지는 사용자 메시지만 발행")
    void handleTalkMessageWithoutTravel() {

        // given
        Long roomId = 1L;
        String username = "testUser@example.com";

        SendChatMessageRequest request =
                new SendChatMessageRequest(
                        MessageType.TALK,
                        "안녕하세요."
                );

        when(chatFacade
                .findTravelIdByRoomId(roomId))
                .thenReturn(Optional.empty());

        // when
        chatMessageFacade.handleMessage(
                roomId,
                request,
                username
        );

        // then
        verify(chatFacade)
                .publishMessage(
                        roomId,
                        request,
                        username
                );

        verifyNoInteractions(travelFacade);

        verify(chatFacade, never())
                .publishTalkReply(
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("CONFIRM 메시지는 여행 편집 확정 후 완료 응답을 발행")
    void handleConfirmMessage() {

        // given
        Long roomId = 1L;
        Long travelId = 100L;
        String username = "testUser@example.com";

        SendChatMessageRequest request =
                new SendChatMessageRequest(
                        MessageType.CONFIRM,
                        null
                );

        when(chatFacade
                .getTravelIdByRoomId(roomId))
                .thenReturn(travelId);

        // when
        chatMessageFacade.handleMessage(
                roomId,
                request,
                username
        );

        // then
        InOrder inOrder = inOrder(
                chatFacade,
                travelFacade
        );

        inOrder.verify(chatFacade)
                .getTravelIdByRoomId(roomId);

        inOrder.verify(travelFacade)
                .confirmEditPlan(
                        new GetAiPlanRequest(travelId),
                        username
                );

        inOrder.verify(chatFacade)
                .publishConfirmReply(roomId);
    }

    @Test
    @DisplayName("CANCEL 메시지는 여행 편집 취소 후 완료 응답을 발행")
    void handleCancelMessage() {

        // given
        Long roomId = 1L;
        Long travelId = 100L;
        String username = "testUser@example.com";

        SendChatMessageRequest request =
                new SendChatMessageRequest(
                        MessageType.CANCEL,
                        null
                );

        when(chatFacade
                .getTravelIdByRoomId(roomId))
                .thenReturn(travelId);

        // when
        chatMessageFacade.handleMessage(
                roomId,
                request,
                username
        );

        // then
        InOrder inOrder = inOrder(
                chatFacade,
                travelFacade
        );

        inOrder.verify(chatFacade)
                .getTravelIdByRoomId(roomId);

        inOrder.verify(travelFacade)
                .cancelEditPlan(
                        new GetAiPlanRequest(travelId),
                        username
                );

        inOrder.verify(chatFacade)
                .publishCancelReply(roomId);
    }

    @Test
    @DisplayName("STOMP 입력에서 지원하지 않는 메시지 타입은 거부")
    void handleUnsupportedMessageType() {

        // given
        SendChatMessageRequest request =
                new SendChatMessageRequest(
                        MessageType.ENTER,
                        null
                );

        // when & then
        assertThatThrownBy(() ->
                chatMessageFacade.handleMessage(
                        1L,
                        request,
                        "testUser@example.com"
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("지원하지 않는 메시지 타입입니다.");

        verifyNoInteractions(
                chatFacade,
                travelFacade
        );
    }

    @Test
    @DisplayName("메시지 타입이 없는 STOMP 입력 거부")
    void handleMessageWithoutType() {

        // given
        SendChatMessageRequest request =
                new SendChatMessageRequest(
                        null,
                        "일정을 수정해 주세요."
                );

        // when & then
        assertThatThrownBy(() ->
                chatMessageFacade.handleMessage(
                        1L,
                        request,
                        "testUser@example.com"
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("메시지 타입은 필수입니다.");

        verifyNoInteractions(
                chatFacade,
                travelFacade
        );
    }

    @Test
    @DisplayName("내용이 비어 있는 TALK 메시지 거부")
    void handleBlankTalkMessage() {

        // given
        SendChatMessageRequest request =
                new SendChatMessageRequest(
                        MessageType.TALK,
                        "  "
                );

        // when & then
        assertThatThrownBy(() ->
                chatMessageFacade.handleMessage(
                        1L,
                        request,
                        "testUser@example.com"
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("TALK 메시지 내용은 필수입니다.");

        verifyNoInteractions(
                chatFacade,
                travelFacade
        );
    }
}
