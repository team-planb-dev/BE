package com.planb.domain.chat.facade;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.travel.dto.request.EditPlanRequest;
import com.planb.domain.travel.dto.request.GetAiPlanRequest;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.facade.TravelFacade;

import java.util.Optional;

/**
 * STOMP 채팅 메시지와 여행 일정 편집 흐름을 조합하는 Facade.
 *
 * Controller가 전송 방식만 처리하도록 메시지 유형별 업무 순서를 캡슐화하고,
 * 채팅 저장과 여행 편집의 기존 트랜잭션 단위를 유지한다.
 */
@Component
@RequiredArgsConstructor
public class ChatMessageFacade {

    private final ChatFacade chatFacade;
    private final TravelFacade travelFacade;

    /**
     * STOMP 메시지 유형에 맞는 채팅 및 여행 편집 흐름을 선택한다.
     *
     * @param roomId 메시지를 보낸 채팅방 ID
     * @param request 메시지 유형과 내용
     * @param username 메시지를 보낸 사용자의 username
     * @throws IllegalArgumentException 사용자가 전송할 수 없는 메시지 유형인 경우
     */
    public void handleMessage(
            Long roomId,
            SendChatMessageRequest request,
            String username
    ) {

        validateRequest(request);

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
     * 메시지 유형과 TALK 내용을 STOMP 입력 경계에서 검증한다.
     *
     * @param request 검증할 메시지 요청
     * @throws IllegalArgumentException 필수 메시지 정보가 없는 경우
     */
    private void validateRequest(SendChatMessageRequest request) {

        if (request.type() == null) {
            throw new IllegalArgumentException(
                    "메시지 타입은 필수입니다."
            );
        }

        if (request.type() == MessageType.TALK
                && (request.message() == null
                || request.message().isBlank())) {

            throw new IllegalArgumentException(
                    "TALK 메시지 내용은 필수입니다."
            );
        }
    }

    /**
     * 사용자 메시지를 먼저 보존하고 여행 채팅방인 경우에만 수정 미리보기를 발행한다.
     *
     * @param roomId 메시지를 보낸 채팅방 ID
     * @param request 사용자의 자연어 수정 요청
     * @param username 메시지를 보낸 사용자의 username
     */
    private void handleTalk(
            Long roomId,
            SendChatMessageRequest request,
            String username
    ) {

        chatFacade.publishMessage(
                roomId,
                request,
                username
        );

        Optional<Long> travelId =
                chatFacade.findTravelIdByRoomId(roomId);

        if (travelId.isEmpty()) {
            return;
        }

        EditPlanPreviewResponse preview =
                travelFacade.makeEditPlanPreview(
                        new EditPlanRequest(
                                travelId.get(),
                                request.message()
                        ),
                        username
                );

        chatFacade.publishTalkReply(
                roomId,
                preview
        );
    }

    /**
     * 여행 수정안을 확정한 뒤 성공 응답을 발행해 처리 순서를 보장한다.
     *
     * @param roomId 수정안을 확정할 채팅방 ID
     * @param username 확정을 요청한 사용자의 username
     */
    private void handleConfirm(
            Long roomId,
            String username
    ) {

        Long travelId =
                chatFacade.getTravelIdByRoomId(roomId);

        travelFacade.confirmEditPlan(
                new GetAiPlanRequest(travelId),
                username
        );

        chatFacade.publishConfirmReply(roomId);
    }

    /**
     * 여행 수정안을 취소한 뒤 완료 응답을 발행해 기존 일정을 유지한다.
     *
     * @param roomId 수정안을 취소할 채팅방 ID
     * @param username 취소를 요청한 사용자의 username
     */
    private void handleCancel(
            Long roomId,
            String username
    ) {

        Long travelId =
                chatFacade.getTravelIdByRoomId(roomId);

        travelFacade.cancelEditPlan(
                new GetAiPlanRequest(travelId),
                username
        );

        chatFacade.publishCancelReply(roomId);
    }
}
