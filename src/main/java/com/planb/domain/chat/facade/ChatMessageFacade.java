package com.planb.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.chat.service.ChatMessageService;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.service.PlanEditCacheService;
import com.planb.domain.travel.service.PlanService;
import com.planb.domain.travel.service.TravelService;
import com.planb.query.chat.service.ChatRoomQueryService;
import com.planb.query.travel.service.TravelQueryService;
import com.planb.query.user.service.UserQueryService;

import java.util.Optional;

/**
 * STOMP 채팅 메시지와 여행 일정 편집 흐름을 조합
 */
@Component
@RequiredArgsConstructor
public class ChatMessageFacade {

    private final ChatMessageService chatMessageService;
    private final ChatRoomQueryService chatRoomQueryService;
    private final UserQueryService userQueryService;
    private final TravelQueryService travelQueryService;
    private final TravelService travelService;
    private final PlanService planService;
    private final PlanEditCacheService planEditCacheService;

    /**
     * 메시지 유형에 맞는 채팅 처리 흐름을 선택
     */
    public void handleMessage(
            Long roomId,
            SendChatMessageRequest request,
            String username
    ) {

        chatMessageService.validateRequest(request); // STOMP 메시지 검증

        switch (request.type()) {
            case TALK -> handleTalk(
                    roomId,
                    request,
                    username
            );
            case CONFIRM -> handleConfirm(
                    roomId,
                    username
            );
            case CANCEL -> handleCancel(
                    roomId,
                    username
            );
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 메시지 타입입니다."
            );
        }
    }

    /**
     * 사용자 대화 저장 및 여행 채팅방 수정안 발행
     */
    private void handleTalk(
            Long roomId,
            SendChatMessageRequest request,
            String username
    ) {

        chatMessageService.publishUserMessage(
                roomId,
                request.message(),
                username
        ); // 사용자 메시지 저장 및 발행

        Optional<Long> travelId =
                chatRoomQueryService.findTravelIdByRoomId(roomId); // 연결된 여행 조회

        if (travelId.isEmpty()) {
            return;
        }

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        travelQueryService.validateOwner(travelId.get(), userId); // 여행 소유권 검증

        PlanEditContext editContext = travelService.prepareEditContext(
                travelId.get(),
                request.message()
        ); // 편집 정보 조회

        EditPlanAiResponse editResponse = planService.makeEditPlanByAi(editContext); // AI 수정안 생성

        planEditCacheService.saveEditResult(travelId.get(), editResponse); // 수정안 캐시 저장

        EditPlanPreviewResponse preview = planService.createEditPreviewResponse(
                editContext,
                editResponse
        ); // 수정 미리보기 생성

        chatMessageService.publishTalkReply(roomId, preview); // AI 응답 발행
    }

    /**
     * 여행 일정 수정안 확정 및 응답 발행
     */
    private void handleConfirm(
            Long roomId,
            String username
    ) {

        Long travelId = chatRoomQueryService.getTravelIdByRoomId(roomId); // 연결된 여행 조회

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        travelQueryService.validateOwner(travelId, userId); // 여행 소유권 검증

        travelService.confirmEditPlanInTransaction(travelId); // 수정안 확정

        chatMessageService.publishConfirmReply(roomId); // 확정 응답 발행
    }

    /**
     * 여행 일정 수정안 취소 및 응답 발행
     */
    private void handleCancel(
            Long roomId,
            String username
    ) {

        Long travelId = chatRoomQueryService.getTravelIdByRoomId(roomId); // 연결된 여행 조회

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        travelQueryService.validateOwner(travelId, userId); // 여행 소유권 검증

        planEditCacheService.deleteEditResult(travelId); // 수정안 취소

        chatMessageService.publishCancelReply(roomId); // 취소 응답 발행
    }
}
