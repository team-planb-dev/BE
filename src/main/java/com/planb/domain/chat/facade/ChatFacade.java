package com.planb.domain.chat.facade;

import com.planb.domain.chat.dto.request.*;
import com.planb.domain.chat.dto.response.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.entity.ChatMessage;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.chat.entity.ChatRoomMember;
import com.planb.domain.chat.service.ChatMessageService;
import com.planb.domain.chat.service.ChatRoomMemberService;
import com.planb.domain.chat.service.ChatRoomService;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.service.TravelService;
import com.planb.domain.user.constant.SystemAccountConstants;
import com.planb.domain.user.entity.User;
import com.planb.global.config.exception.WebSocketExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.config.exception.domain.ForbiddenException;
import com.planb.query.chat.service.ChatMessageQueryService;
import com.planb.query.chat.service.ChatRoomMemberQueryService;
import com.planb.query.chat.service.ChatRoomQueryService;
import com.planb.query.travel.service.TravelQueryService;
import com.planb.query.user.service.UserQueryService;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 채팅방, 멤버십, 메시지의 비즈니스 흐름을 조합하는 Facade.
 *
 * 채팅 데이터의 조회와 변경 순서를 관리하고 사용자 및 AI 메시지가
 * 동일한 채팅방 계약을 따르도록 각 Service를 조합한다.
 */
@Component
@RequiredArgsConstructor
public class ChatFacade {

    private final ChatRoomQueryService chatRoomQueryService;
    private final ChatRoomMemberQueryService chatRoomMemberQueryService;
    private final ChatMessageQueryService chatMessageQueryService;
    private final TravelQueryService travelQueryService;

    private final UserQueryService userQueryService;

    private final ChatRoomService chatRoomService;
    private final ChatRoomMemberService chatRoomMemberService;
    private final ChatMessageService chatMessageService;
    private final TravelService travelService;


    /**
     * 일반 채팅방 생성 흐름을 수행한다.
     *
     * @param request 생성할 채팅방 정보
     * @return 생성된 채팅방 정보
     */
    @Transactional
    public CreateChatRoomResponse createChatRoom
    (CreateChatRoomRequest request){

        return chatRoomService.createChatRoom(request);
    }

    /**
     * 여행 소유권을 검증한 뒤 여행 전용 채팅방을 조회하거나 생성한다.
     *
     * 반복 호출에도 동일한 채팅방을 반환하며, 소유자가 STOMP 권한 검증을
     * 통과할 수 있도록 채팅방 멤버십을 한 번만 보장한다.
     *
     * @param travelId 채팅방과 연결할 여행 ID
     * @param username 요청한 사용자의 username
     * @return 조회하거나 생성한 여행 채팅방 정보
     * @throws ForbiddenException 사용자가 해당 여행의 소유자가 아닌 경우
     */
    @Transactional
    public CreateChatRoomResponse findOrCreateTravelChatRoom
    (Long travelId, String username){

        User user = userQueryService
                .findByUsername(username);

        Long userId = user.getId();

        if (!travelQueryService.existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }

        return chatRoomQueryService
                .findChatRoomByTravelId(travelId)
                .map(chatRoom -> {
                    ensureChatRoomMember(
                            chatRoom,
                            user
                    );

                    return new CreateChatRoomResponse(
                            chatRoom.getId(),
                            chatRoom.getChatRoomName(),
                            chatRoom.getCreatedAt(),
                            "채팅방이 조회되었습니다."
                    );
                })
                .orElseGet(() -> {

                    Travel travel = travelService
                            .findTravelById(travelId);

                    ChatRoom chatRoom = chatRoomService
                            .createChatRoomForTravel(travel);

                    ensureChatRoomMember(
                            chatRoom,
                            user
                    );

                    return new CreateChatRoomResponse(
                            chatRoom.getId(),
                            chatRoom.getChatRoomName(),
                            chatRoom.getCreatedAt(),
                            "채팅방이 생성되었습니다.");
                });
    }

    /**
     * 여행 채팅방의 반복 조회가 중복 가입 오류를 만들지 않도록 멤버십을 보장한다.
     *
     * @param chatRoom 멤버십을 확인할 채팅방
     * @param user 채팅방에 참여할 사용자
     */
    private void ensureChatRoomMember(
            ChatRoom chatRoom,
            User user
    ) {

        boolean alreadyMember = chatRoomMemberQueryService
                .checkSubscriberWithRoomId(
                        chatRoom.getId(),
                        user.getId()
                );

        if (alreadyMember) {
            return;
        }

        chatRoomMemberService
                .addChatUser(
                        new AddChatUserRequest(
                                chatRoom,
                                user
                        )
                );
    }

    /**
     * 일정 확정 및 취소에 필요한 여행 연결 정보를 조회한다.
     *
     * 여행과 연결되지 않은 일반 채팅방은 일정 편집을 수행할 수 없으므로 예외로 거부한다.
     *
     * @param roomId 여행 연결 정보를 조회할 채팅방 ID
     * @return 채팅방과 연결된 여행 ID
     * @throws BaseException 채팅방이 여행과 연결되지 않은 경우
     */
    public Long getTravelIdByRoomId(Long roomId){

        ChatRoom chatRoom =
                chatRoomQueryService.findChatRoomByRoomId(roomId);

        if (chatRoom.getTravel() == null) {
            throw new BaseException(
                    WebSocketExceptionEnum.TRAVEL_NOT_LINKED
            );
        }

        return chatRoom.getTravel().getId();
    }

    /**
     * 일반 대화와 여행 편집 대화를 구분하기 위해 여행 연결 정보를 조회한다.
     *
     * @param roomId 여행 연결 정보를 조회할 채팅방 ID
     * @return 연결된 여행 ID 또는 여행 미연결을 나타내는 빈 값
     */
    public Optional<Long> findTravelIdByRoomId(Long roomId){

        ChatRoom chatRoom =
                chatRoomQueryService.findChatRoomByRoomId(roomId);

        return Optional.ofNullable(chatRoom.getTravel())
                .map(Travel::getId);
    }

    /**
     * AI 시스템 계정의 identity를 유지한 채 응답을 저장하고 실시간 발행한다.
     *
     * @param roomId 응답을 발행할 채팅방 ID
     * @param message 사용자에게 전달할 AI 메시지
     * @param editPreview 일정 수정 미리보기
     * @param type AI 응답의 메시지 유형
     */
    @Transactional
    public void publishAiReply(
            Long roomId,
            String message,
            EditPlanPreviewResponse editPreview,
            MessageType type
    ) {

        User aiUser = userQueryService
                .findByUsername(SystemAccountConstants.AI_BOT_USERNAME);

        ChatRoom chatRoom =
                chatRoomQueryService.findChatRoomByRoomId(roomId);

        ChatMessage chatMessage =
                chatMessageService.createChatMessage(
                        chatRoom,
                        aiUser,
                        message);

        chatMessageService.saveMessage(chatMessage);

        chatMessageService
                .publishMessage(
                        roomId,
                        chatMessageService
                                .makeAiChatResponse(
                                        roomId,
                                        aiUser,
                                        chatMessage,
                                        editPreview,
                                        type));
    }

    /**
     * 일정 수정 가능 여부에 따라 TALK 응답 내용과 미리보기 노출 여부를 결정한다.
     *
     * @param roomId 응답을 발행할 채팅방 ID
     * @param preview 일정 수정 미리보기 결과
     */
    @Transactional
    public void publishTalkReply(Long roomId,
                                 EditPlanPreviewResponse preview){

        AiReplyContent content =
                chatMessageService.resolveAiReplyContent(preview);

        publishAiReply(
                roomId,
                content.message(),
                content.editPreview(),
                MessageType.TALK
        );
    }

    /**
     * 여행 채팅방의 첫 대화에서만 AI 안내 메시지를 발행한다.
     *
     * 일반 채팅방이거나 이미 메시지가 존재하면 기존 대화 흐름을 보존하기 위해 발행하지 않는다.
     *
     * @param roomId 입장한 채팅방 ID
     * @param username 입장한 사용자의 username
     */
    @Transactional
    public void publishAiGreetingIfNeeded(Long roomId, String username){

        Optional<Long> travelId = findTravelIdByRoomId(roomId);

        if (travelId.isEmpty()) {
            return;
        }

        if (chatMessageService.existsAnyMessage(roomId)) {
            return;
        }

        User participant = userQueryService.findByUsername(username);

        User aiUser = userQueryService
                .findByUsername(SystemAccountConstants.AI_BOT_USERNAME);

        List<String> greetingMessages =
                chatMessageService.resolveGreetingMessages(
                        participant.getNickname(),
                        aiUser.getNickname()
                );

        greetingMessages.forEach(message ->
                publishAiReply(roomId, message, null, MessageType.TALK)
        );
    }

    /**
     * 일정 수정 확정이 완료된 뒤 CONFIRM 유형의 고정 응답을 발행한다.
     *
     * @param roomId 응답을 발행할 채팅방 ID
     */
    @Transactional
    public void publishConfirmReply(Long roomId){

        publishAiReply(
                roomId,
                chatMessageService.resolveConfirmMessage(),
                null,
                MessageType.CONFIRM
        );
    }

    /**
     * 일정 수정 취소가 완료된 뒤 CANCEL 유형의 고정 응답을 발행한다.
     *
     * @param roomId 응답을 발행할 채팅방 ID
     */
    @Transactional
    public void publishCancelReply(Long roomId){

        publishAiReply(
                roomId,
                chatMessageService.resolveCancelMessage(),
                null,
                MessageType.CANCEL
        );
    }

    /**
     * 중복 참여를 검증한 뒤 사용자를 일반 채팅방 멤버로 등록한다.
     *
     * @param addChatRoomMemberRequest 채팅방과 사용자 식별 정보
     * @param username 요청한 사용자의 username
     * @return 등록된 채팅방 멤버 정보
     * @throws ForbiddenException 요청 사용자가 자신이 아닌 멤버를 대신 등록하는 경우
     */
    @Transactional
    public AddChatUserResponse addChatUser(
            AddChatRoomMemberRequest addChatRoomMemberRequest,
            String username
    ) {

        User user = userQueryService
                .findByUsername(username);

        validateRequestedUser(
                addChatRoomMemberRequest.userId(),
                user
        );

        chatRoomMemberQueryService
                .validateDuplicateMemberWithRoom(
                        addChatRoomMemberRequest
                                .roomId(),
                        addChatRoomMemberRequest
                                .userId());

        ChatRoom chatRoom = chatRoomQueryService
                .findChatRoomByRoomId(addChatRoomMemberRequest
                        .roomId());

        validateTravelRoomOwner(
                chatRoom,
                user
        );

        return chatRoomMemberService
                .addChatUser(new AddChatUserRequest(
                        chatRoom,
                        user));
    }

    /**
     * 채팅방, 사용자, 멤버십의 일치 관계를 확인한 뒤 멤버를 삭제한다.
     *
     * @param deleteChatRoomMemberRequest 삭제할 채팅방 멤버 식별 정보
     * @param username 요청한 사용자의 username
     * @return 삭제된 채팅방 멤버 정보
     * @throws ForbiddenException 요청 사용자가 자신이 아닌 멤버를 대신 삭제하는 경우
     */
    @Transactional
    public DeleteChatUserResponse deleteChatUser(
            DeleteChatRoomMemberRequest deleteChatRoomMemberRequest,
            String username
    ) {

        User user = userQueryService
                .findByUsername(username);

        validateRequestedUser(
                deleteChatRoomMemberRequest.userId(),
                user
        );

        ChatRoomMember chatRoomMember = chatRoomMemberQueryService
                .findByUserId(
                        deleteChatRoomMemberRequest
                                .roomId(),
                        deleteChatRoomMemberRequest
                                .userId());
        ChatRoom chatRoom = chatRoomQueryService
                .findChatRoomByRoomId(deleteChatRoomMemberRequest
                        .roomId());

        return chatRoomMemberService
                .deleteChatUser(new DeleteChatUserRequest(
                        chatRoomMember,
                        chatRoom,
                        user));
    }


    /**
     * 참조 무결성을 유지하도록 멤버 관계를 먼저 제거한 뒤 채팅방과 메시지를 삭제한다.
     *
     * @param request 삭제할 채팅방 정보
     * @param username 삭제를 요청한 사용자의 username
     * @return 삭제된 채팅방과 보관된 메시지 정보
     * @throws ForbiddenException 요청 사용자가 해당 채팅방 멤버가 아닌 경우
     */
    @Transactional
    public DeleteChatRoomResponse deleteChatRoom(
            DeleteChatRoomRequest request,
            String username
    ) {

        User user = userQueryService
                .findByUsername(username);

        if (!chatRoomMemberQueryService
                .checkSubscriberWithRoomId(
                        request.roomId(),
                        user.getId()
                )) {

            throw new ForbiddenException(
                    new Object[]{"해당 채팅방에 대한 접근 권한이 없습니다."}
            );
        }

        ChatRoom chatRoom = chatRoomQueryService
                .findChatRoomByRoomId(request
                        .roomId());

        validateTravelRoomOwner(
                chatRoom,
                user
        );

        chatRoomMemberQueryService
                .deleteAllChatRoomMemberByRoomId(chatRoom
                        .getId());

        chatRoomService.deleteChatRoom(chatRoom);

        Long deletedCount = chatMessageQueryService
                .softDeleteAllMessageInChatRoom(request
                        .roomId());

        return new DeleteChatRoomResponse(
                request.roomId(),
                chatRoom.getChatRoomName(),
                "삭제 보관된 메시지 갯수: "+deletedCount,
                "채팅방이 삭제되었습니다.");

    }

    /**
     * 여행 채팅방은 해당 여행 소유자만 변경할 수 있도록 검증한다.
     *
     * 일반 채팅방은 기존 멤버십 정책을 유지한다.
     *
     * @param chatRoom 권한을 확인할 채팅방
     * @param authenticatedUser 인증된 사용자
     * @throws ForbiddenException 여행 소유자가 아닌 경우
     */
    private void validateTravelRoomOwner(
            ChatRoom chatRoom,
            User authenticatedUser
    ) {

        Travel travel = chatRoom.getTravel();

        if (travel == null) {
            return;
        }

        if (!travel
                .getUser()
                .getId()
                .equals(authenticatedUser.getId())) {

            throw new ForbiddenException(
                    new Object[]{"해당 여행 채팅방에 대한 접근 권한이 없습니다."}
            );
        }
    }

    /**
     * 요청 본문의 사용자와 인증 사용자가 같은지 확인한다.
     *
     * @param requestedUserId 요청 본문에 포함된 사용자 ID
     * @param authenticatedUser 인증된 사용자
     * @throws ForbiddenException 다른 사용자의 멤버십을 변경하려는 경우
     */
    private void validateRequestedUser(
            Long requestedUserId,
            User authenticatedUser
    ) {

        if (!authenticatedUser.getId().equals(requestedUserId)) {
            throw new ForbiddenException(
                    new Object[]{"다른 사용자의 채팅방 멤버십을 변경할 수 없습니다."}
            );
        }
    }


    /**
     * 사용자 메시지를 대화 기록에 저장한 뒤 같은 채팅방 구독자에게 발행한다.
     *
     * @param roomId 메시지를 발행할 채팅방 ID
     * @param request 메시지 유형과 내용
     * @param username 메시지를 보낸 사용자의 username
     */
    @Transactional
    public void publishMessage(
            Long roomId,
            SendChatMessageRequest request,
            String username
    ) {

        ChatRoom chatRoom =
                chatRoomQueryService.findChatRoomByRoomId(roomId);

        User sender = userQueryService
                .findByUsername(username);

        ChatMessage chatMessage =
                chatMessageService.createChatMessage(
                        chatRoom,
                        sender,
                        request
                                .message());

        chatMessageService.saveMessage(chatMessage);

        chatMessageService
                .publishMessage(
                        roomId,
                        chatMessageService
                                .makeChatResponse(
                                        roomId,
                                        sender,
                                        chatMessage));
    }

    /**
     * 입장과 퇴장 상태는 대화 기록에 남기지 않고 현재 구독자에게만 실시간 발행한다.
     *
     * @param roomId 상태 메시지를 발행할 채팅방 ID
     * @param username 상태가 변경된 사용자의 username
     * @param messageType 입장 또는 퇴장 메시지 유형
     */
    public void publishSystemMessage(
            Long roomId,
            String username,
            MessageType messageType
    ) {


        User participant = userQueryService.findByUsername(username);

        String systemMessage = chatMessageService
                .createSystemMessage(messageType, participant.getNickname());

        SendChatMessageResponse response = new SendChatMessageResponse(
                messageType,
                roomId,
                participant.getId(),
                participant.getNickname(),
                systemMessage,
                null,
                Instant.now()
        );

        chatMessageService
                .publishMessage(roomId,response);
    }
}
