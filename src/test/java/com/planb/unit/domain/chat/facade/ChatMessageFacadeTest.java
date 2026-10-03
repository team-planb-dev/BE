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
import com.planb.domain.chat.facade.ChatMessageFacade;
import com.planb.domain.chat.service.ChatMessageService;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.service.PlanEditCacheService;
import com.planb.domain.travel.service.PlanService;
import com.planb.domain.travel.service.TravelService;
import com.planb.query.chat.service.ChatRoomQueryService;
import com.planb.query.travel.service.TravelQueryService;
import com.planb.query.user.service.UserQueryService;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatMessageFacadeTest {

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private ChatRoomQueryService chatRoomQueryService;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private TravelQueryService travelQueryService;

    @Mock
    private TravelService travelService;

    @Mock
    private PlanService planService;

    @Mock
    private PlanEditCacheService planEditCacheService;

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

        PlanEditContext editContext = mock(PlanEditContext.class);
        EditPlanAiResponse editResponse = mock(EditPlanAiResponse.class);
        EditPlanPreviewResponse preview = mock(EditPlanPreviewResponse.class);

        when(chatRoomQueryService.findTravelIdByRoomId(roomId))
                .thenReturn(Optional.of(travelId));

        when(userQueryService.findUserIdInCache(username))
                .thenReturn(50L);

        when(travelService.prepareEditContext(travelId, request.message()))
                .thenReturn(editContext);

        when(planService.makeEditPlanByAi(editContext))
                .thenReturn(editResponse);

        when(planService.createEditPreviewResponse(editContext, editResponse))
                .thenReturn(preview);

        // when
        chatMessageFacade.handleMessage(
                roomId,
                request,
                username
        );

        // then
        InOrder inOrder = inOrder(
                chatMessageService,
                chatRoomQueryService,
                userQueryService,
                travelQueryService,
                travelService,
                planService,
                planEditCacheService
        );

        inOrder
                .verify(chatMessageService)
                .validateRequest(request);

        inOrder
                .verify(chatMessageService)
                .publishUserMessage(
                        roomId,
                        request.message(),
                        username
                );

        inOrder
                .verify(chatRoomQueryService)
                .findTravelIdByRoomId(roomId);

        inOrder
                .verify(userQueryService)
                .findUserIdInCache(username);

        inOrder
                .verify(travelQueryService)
                .validateOwner(travelId, 50L);

        inOrder
                .verify(travelService)
                .prepareEditContext(travelId, request.message());

        inOrder
                .verify(planService)
                .makeEditPlanByAi(editContext);

        inOrder
                .verify(planEditCacheService)
                .saveEditResult(travelId, editResponse);

        inOrder
                .verify(planService)
                .createEditPreviewResponse(editContext, editResponse);

        inOrder
                .verify(chatMessageService)
                .publishTalkReply(roomId, preview);
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

        when(chatRoomQueryService
                .findTravelIdByRoomId(roomId))
                .thenReturn(Optional.empty());

        // when
        chatMessageFacade.handleMessage(
                roomId,
                request,
                username
        );

        // then
        verify(chatMessageService)
                .publishUserMessage(
                        roomId,
                        request.message(),
                        username
                );

        verifyNoInteractions(
                travelService,
                planService,
                planEditCacheService,
                travelQueryService
        );

        verify(chatMessageService, never())
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

        when(chatRoomQueryService
                .getTravelIdByRoomId(roomId))
                .thenReturn(travelId);

        when(userQueryService.findUserIdInCache(username))
                .thenReturn(50L);

        // when
        chatMessageFacade.handleMessage(
                roomId,
                request,
                username
        );

        // then
        InOrder inOrder = inOrder(
                chatMessageService,
                chatRoomQueryService,
                userQueryService,
                travelQueryService,
                travelService
        );

        inOrder
                .verify(chatMessageService)
                .validateRequest(request);

        inOrder
                .verify(chatRoomQueryService)
                .getTravelIdByRoomId(roomId);

        inOrder
                .verify(userQueryService)
                .findUserIdInCache(username);

        inOrder
                .verify(travelQueryService)
                .validateOwner(travelId, 50L);

        inOrder
                .verify(travelService)
                .confirmEditPlanInTransaction(travelId);

        inOrder
                .verify(chatMessageService)
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

        when(chatRoomQueryService
                .getTravelIdByRoomId(roomId))
                .thenReturn(travelId);

        when(userQueryService.findUserIdInCache(username))
                .thenReturn(50L);

        // when
        chatMessageFacade.handleMessage(
                roomId,
                request,
                username
        );

        // then
        InOrder inOrder = inOrder(
                chatMessageService,
                chatRoomQueryService,
                userQueryService,
                travelQueryService,
                planEditCacheService
        );

        inOrder
                .verify(chatMessageService)
                .validateRequest(request);

        inOrder
                .verify(chatRoomQueryService)
                .getTravelIdByRoomId(roomId);

        inOrder
                .verify(userQueryService)
                .findUserIdInCache(username);

        inOrder
                .verify(travelQueryService)
                .validateOwner(travelId, 50L);

        inOrder
                .verify(planEditCacheService)
                .deleteEditResult(travelId);

        inOrder
                .verify(chatMessageService)
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
                chatRoomQueryService,
                travelService,
                planService,
                planEditCacheService
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

        doThrow(new IllegalArgumentException("메시지 타입은 필수입니다."))
                .when(chatMessageService)
                .validateRequest(request);

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
                chatRoomQueryService,
                travelService,
                planService,
                planEditCacheService
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

        doThrow(new IllegalArgumentException("TALK 메시지 내용은 필수입니다."))
                .when(chatMessageService)
                .validateRequest(request);

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
                chatRoomQueryService,
                travelService,
                planService,
                planEditCacheService
        );
    }
}
