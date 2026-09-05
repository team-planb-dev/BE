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
import java.util.Optional;

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
     *채팅방 생성하기
     */
    @Transactional
    public CreateChatRoomResponse createChatRoom
    (CreateChatRoomRequest request){

        return chatRoomService.createChatRoom(request);
    }

    /**
     * travelId 기준으로 채팅방 조회, 없으면 생성
     */
    @Transactional
    public CreateChatRoomResponse findOrCreateTravelChatRoom
    (Long travelId, String username){

        Long userId = userQueryService
                .findByUsername(username)
                .getId();

        // travelId가 이 userId 소유인지 검증
        if (!travelQueryService.existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }

        return chatRoomQueryService
                .findChatRoomByTravelId(travelId)
                .map(chatRoom -> new CreateChatRoomResponse(
                        chatRoom.getId(),
                        chatRoom.getChatRoomName(),
                        chatRoom.getCreatedAt(),
                        "채팅방이 조회되었습니다."))
                .orElseGet(() -> {

                    Travel travel = travelService
                            .findTravelById(travelId);

                    ChatRoom chatRoom = chatRoomService
                            .createChatRoomForTravel(travel);

                    return new CreateChatRoomResponse(
                            chatRoom.getId(),
                            chatRoom.getChatRoomName(),
                            chatRoom.getCreatedAt(),
                            "채팅방이 생성되었습니다.");
                });
    }

    /**
     * roomId 기준으로 travelId 조회
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
     * roomId 기준으로 travelId 조회 (travel 미연결 시 빈 값)
     */
    public Optional<Long> findTravelIdByRoomId(Long roomId){

        ChatRoom chatRoom =
                chatRoomQueryService.findChatRoomByRoomId(roomId);

        return Optional.ofNullable(chatRoom.getTravel())
                .map(Travel::getId);
    }

    /**
     * AI 봇 명의 메시지 저장 및 전달하기
     */
    @Transactional
    public void publishAiReply(Long roomId,
                               String message,
                               EditPlanPreviewResponse editPreview,
                               MessageType type){

        // AI 봇 계정 조회
        User aiUser = userQueryService
                .findByUsername(SystemAccountConstants.AI_BOT_USERNAME);

        // ChatRoom 조회
        ChatRoom chatRoom =
                chatRoomQueryService.findChatRoomByRoomId(roomId);

        // ChatMessage 객체 생성 및 저장
        ChatMessage chatMessage =
                chatMessageService.createChatMessage(
                        chatRoom,
                        aiUser,
                        message);

        chatMessageService.saveMessage(chatMessage);

        // publish 메소드 호출
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
     * TALK 메시지에 대한 AI 응답 발행
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
     * 채팅방에 User 등록하기
     */
    @Transactional
    public AddChatUserResponse addChatUser(AddChatRoomMemberRequest addChatRoomMemberRequest){

        // 중복 여부 확인하기
        chatRoomMemberQueryService
                .validateDuplicateMemberWithRoom(
                        addChatRoomMemberRequest
                                .roomId(),
                        addChatRoomMemberRequest
                                .userId());

        // 유저 불러오기
        User user = userQueryService
                .findById(addChatRoomMemberRequest
                        .userId());

        // 채팅방 불러오기
        ChatRoom chatRoom = chatRoomQueryService
                .findChatRoomByRoomId(addChatRoomMemberRequest
                        .roomId());

        // 채팅방에 유저 등록하기
        return chatRoomMemberService
                .addChatUser(new AddChatUserRequest(
                        chatRoom,
                        user));
    }

    /**
     * 채팅방 User 삭제하기
     */
    @Transactional
    public DeleteChatUserResponse deleteChatUser(DeleteChatRoomMemberRequest deleteChatRoomMemberRequest){

        // ChatRoomMember 객체 불러오기
        ChatRoomMember chatRoomMember = chatRoomMemberQueryService
                .findByUserId(
                        deleteChatRoomMemberRequest
                                .roomId(),
                        deleteChatRoomMemberRequest
                                .userId());
        // User 객체 불러오기
        User user = userQueryService
                .findById(deleteChatRoomMemberRequest
                        .userId());

        // ChatRoom 객체 불러오기
        ChatRoom chatRoom = chatRoomQueryService
                .findChatRoomByRoomId(deleteChatRoomMemberRequest
                        .roomId());

        // 채팅방에서 유저 삭제하기
        return chatRoomMemberService
                .deleteChatUser(new DeleteChatUserRequest(
                        chatRoomMember,
                        chatRoom,
                        user));
    }


    /**
     * 채팅방 삭제하기
     */
    @Transactional
    public DeleteChatRoomResponse deleteChatRoom
    (DeleteChatRoomRequest request){

        // roomId로 채팅방 조회
        ChatRoom chatRoom = chatRoomQueryService
                .findChatRoomByRoomId(request
                        .roomId());

        // chatRoomMember 관계 삭제(hard)
        chatRoomMemberQueryService
                .deleteAllChatRoomMemberByRoomId(chatRoom
                        .getId());

        // 채팅방 삭제(soft)
        chatRoomService.deleteChatRoom(chatRoom);

        // 채팅방 대화내역 삭제(soft)
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
     * 채팅방 메시지 전달하기
     */
    @Transactional
    public void publishMessage(Long roomId,
                               SendChatMessageRequest request,
                               String username){

        // ChatRoom 조회
        ChatRoom chatRoom =
                chatRoomQueryService.findChatRoomByRoomId(roomId);

        // User 조회
        User sender = userQueryService
                .findByUsername(username);

        // ChatMessage 객체 생성
        ChatMessage chatMessage =
                chatMessageService.createChatMessage(
                        chatRoom,
                        sender,
                        request
                                .message());

        // chatMessage 객체 저장
        chatMessageService.saveMessage(chatMessage);

        // publish 메소드 호출
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
     * 시스템 메시지 생성하기
     */
    public void publishSystemMessage(Long roomId,
                                     String username,
                                     MessageType messageType){


        // 참여자 객체(user) 가져오기
        User participant = userQueryService.findByUsername(username);

        // messageType에 따른 시스템 메시지 생성
        String systemMessage = chatMessageService
                .createSystemMessage(messageType, participant.getNickname());

        // response객체 생성
        SendChatMessageResponse response = new SendChatMessageResponse(
                messageType,
                roomId,
                participant.getId(),
                participant.getNickname(),
                systemMessage,
                null,
                Instant.now()
        );

        // 해당 방에 메시지 전송
        chatMessageService
                .publishMessage(roomId,response);
    }
}
