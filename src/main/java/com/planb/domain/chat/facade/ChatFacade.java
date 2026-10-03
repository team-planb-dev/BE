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
import com.planb.domain.user.entity.User;
import com.planb.global.config.exception.WebSocketExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.query.chat.service.ChatMessageQueryService;
import com.planb.query.chat.service.ChatRoomMemberQueryService;
import com.planb.query.chat.service.ChatRoomQueryService;
import com.planb.query.travel.service.TravelQueryService;
import com.planb.query.user.service.UserQueryService;

import java.util.Optional;

/**
 * 채팅방과 멤버십 및 메시지 처리 흐름을 조합
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
     * 일반 채팅방을 생성
     */
    @Transactional
    public CreateChatRoomResponse createChatRoom
    (CreateChatRoomRequest request) {

        return chatRoomService.createChatRoom(request);
    }

    /**
     * 여행 전용 채팅방 조회 및 생성
     */
    @Transactional
    public CreateChatRoomResponse findOrCreateTravelChatRoom
    (Long travelId, String username) {

        User user = userQueryService.findByUsername(username); // 사용자 조회

        travelQueryService.validateOwner(travelId, user.getId()); // 여행 소유권 검증

        Optional<ChatRoom> existing = chatRoomQueryService.findChatRoomByTravelId(travelId); // 기존 채팅방 조회

        ChatRoom chatRoom;
        if (existing.isPresent()) {
            chatRoom = existing.get();
        } else {
            Travel travel = travelService.findTravelById(travelId); // 여행 조회
            chatRoom = chatRoomService.createChatRoomForTravel(travel); // 채팅방 생성
        }

        chatRoomMemberService.ensureMember(chatRoom, user); // 채팅방 멤버십 보장

        return chatRoomService.travelChatRoomResponse(
                chatRoom,
                existing.isPresent()
        ); // 채팅방 응답 생성
    }

    /**
     * 채팅방에 연결된 여행 ID를 조회
     */
    public Long getTravelIdByRoomId(Long roomId) {

        return chatRoomQueryService.getTravelIdByRoomId(roomId); // 연결된 여행 ID 조회
    }

    /**
     * 채팅방에 연결된 여행 ID를 조회
     */
    public Optional<Long> findTravelIdByRoomId(Long roomId) {

        return chatRoomQueryService.findTravelIdByRoomId(roomId); // 연결된 여행 ID 조회
    }

    /**
     * AI 응답 저장 및 발행
     */
    @Transactional
    public void publishAiReply(
            Long roomId,
            String message,
            EditPlanPreviewResponse editPreview,
            MessageType type
    ) {

        chatMessageService.publishAiReply(
                roomId,
                message,
                editPreview,
                type
        ); // AI 응답 저장 및 발행
    }

    /**
     * 일정 수정 가능 여부에 따라 AI 대화 응답을 발행
     */
    @Transactional
    public void publishTalkReply(Long roomId,
                                 EditPlanPreviewResponse preview) {

        chatMessageService.publishTalkReply(roomId, preview); // 대화 응답 발행
    }

    /**
     * 여행 채팅방의 첫 AI 안내 메시지를 발행
     */
    @Transactional
    public void publishAiGreetingIfNeeded(Long roomId, String username) {

        chatMessageService.publishAiGreetingIfNeeded(roomId, username); // 첫 AI 안내 발행
    }

    /**
     * 일정 수정 확정 응답을 발행
     */
    @Transactional
    public void publishConfirmReply(Long roomId) {

        chatMessageService.publishConfirmReply(roomId); // 확정 응답 발행
    }

    /**
     * 일정 수정 실패 안내를 발행
     */
    @Transactional
    public void publishEditFailedReply(
            Long roomId,
            Exception exception
    ) {

        chatMessageService.publishEditFailedReply(roomId, exception); // 실패 안내 발행
    }

    /**
     * 일정 수정 취소 응답을 발행
     */
    @Transactional
    public void publishCancelReply(Long roomId) {

        chatMessageService.publishCancelReply(roomId); // 취소 응답 발행
    }

    /**
     * 사용자를 일반 채팅방 멤버로 등록
     */
    @Transactional
    public AddChatUserResponse addChatUser(
            AddChatRoomMemberRequest addChatRoomMemberRequest,
            String username
    ) {

        User user = userQueryService
                .findByUsername(username);

        chatRoomMemberService.validateRequestedUser(
                addChatRoomMemberRequest.userId(),
                user
        ); // 요청 사용자 검증

        chatRoomMemberQueryService
                .validateDuplicateMemberWithRoom(
                        addChatRoomMemberRequest
                                .roomId(),
                        addChatRoomMemberRequest
                                .userId());

        ChatRoom chatRoom = chatRoomQueryService
                .findChatRoomByRoomId(addChatRoomMemberRequest
                        .roomId());

        chatRoomMemberService.validateTravelRoomOwner(
                chatRoom,
                user
        ); // 여행 채팅방 소유권 검증

        return chatRoomMemberService
                .addChatUser(
                        chatRoom,
                        user
                ); // 채팅방 멤버 등록
    }

    /**
     * 일반 채팅방 멤버를 삭제
     */
    @Transactional
    public DeleteChatUserResponse deleteChatUser(
            DeleteChatRoomMemberRequest deleteChatRoomMemberRequest,
            String username
    ) {

        User user = userQueryService
                .findByUsername(username);

        chatRoomMemberService.validateRequestedUser(
                deleteChatRoomMemberRequest.userId(),
                user
        ); // 요청 사용자 검증

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
                .deleteChatUser(
                        chatRoomMember,
                        chatRoom,
                        user
                ); // 채팅방 멤버 삭제
    }


    /**
     * 채팅방·멤버십·메시지 삭제
     */
    @Transactional
    public DeleteChatRoomResponse deleteChatRoom(
            DeleteChatRoomRequest request,
            String username
    ) {

        User user = userQueryService
                .findByUsername(username);

        chatRoomMemberService.validateRoomMember(request.roomId(), user); // 채팅방 멤버십 검증

        ChatRoom chatRoom = chatRoomQueryService
                .findChatRoomByRoomId(request
                        .roomId());

        chatRoomMemberService.validateTravelRoomOwner(
                chatRoom,
                user
        ); // 여행 채팅방 소유권 검증

        chatRoomMemberQueryService
                .deleteAllChatRoomMemberByRoomId(chatRoom
                        .getId());

        chatRoomService.deleteChatRoom(chatRoom);

        Long deletedCount = chatMessageQueryService
                .softDeleteAllMessageInChatRoom(request
                        .roomId());

        return chatRoomService.deletedChatRoomResponse(
                chatRoom,
                deletedCount
        ); // 채팅방 삭제 응답 생성

    }

    /**
     * 사용자 메시지 저장 및 발행
     */
    @Transactional
    public void publishMessage(
            Long roomId,
            SendChatMessageRequest request,
            String username
    ) {

        chatMessageService.publishUserMessage(
                roomId,
                request.message(),
                username
        ); // 사용자 메시지 저장 및 발행
    }

    /**
     * 입장 및 퇴장 메시지를 실시간 발행
     */
    public void publishSystemMessage(
            Long roomId,
            String username,
            MessageType messageType
    ) {


        chatMessageService.publishSystemMessage(
                roomId,
                username,
                messageType
        ); // 시스템 메시지 발행
    }
}
