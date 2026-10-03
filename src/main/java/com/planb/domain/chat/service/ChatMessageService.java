package com.planb.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.response.AiReplyContent;
import com.planb.domain.chat.dto.response.SendChatMessageResponse;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.entity.ChatMessage;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.chat.helper.ChatAiReplyMessageHelper;
import com.planb.domain.chat.repository.ChatMessageRepository;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.constant.SystemAccountConstants;
import com.planb.query.chat.service.ChatRoomQueryService;
import com.planb.query.user.service.UserQueryService;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatAiReplyMessageHelper chatAiReplyMessageHelper;
    private final ChatRoomQueryService chatRoomQueryService;
    private final UserQueryService userQueryService;

    public void validateRequest(SendChatMessageRequest request) {

        if (request.type() == null) {
            throw new IllegalArgumentException("메시지 타입은 필수입니다.");
        }

        if (request.type() == MessageType.TALK
                && (request.message() == null || request
                        .message()
                        .isBlank())) {
            throw new IllegalArgumentException("TALK 메시지 내용은 필수입니다.");
        }
    }

    @Transactional
    public void publishUserMessage(
            Long roomId,
            String message,
            String username
    ) {

        ChatRoom chatRoom = chatRoomQueryService.findChatRoomByRoomId(roomId);
        User sender = userQueryService.findByUsername(username);
        ChatMessage chatMessage = createChatMessage(
                chatRoom,
                sender,
                message
        );

        saveMessage(chatMessage);

        SendChatMessageResponse response = makeChatResponse(
                roomId,
                sender,
                chatMessage
        );

        publishMessage(roomId, response);
    }

    @Transactional
    public void publishAiReply(
            Long roomId,
            String message,
            EditPlanPreviewResponse editPreview,
            MessageType type
    ) {

        User aiUser = userQueryService.findByUsername(
                SystemAccountConstants.AI_BOT_USERNAME
        );
        ChatRoom chatRoom = chatRoomQueryService.findChatRoomByRoomId(roomId);
        ChatMessage chatMessage = createChatMessage(
                chatRoom,
                aiUser,
                message
        );

        saveMessage(chatMessage);

        SendChatMessageResponse response = makeAiChatResponse(
                roomId,
                aiUser,
                chatMessage,
                editPreview,
                type
        );

        publishMessage(roomId, response);
    }

    @Transactional
    public void publishTalkReply(Long roomId, EditPlanPreviewResponse preview) {

        AiReplyContent content = resolveAiReplyContent(preview);

        publishAiReply(
                roomId,
                content.message(),
                content.editPreview(),
                MessageType.TALK
        );
    }

    @Transactional
    public void publishConfirmReply(Long roomId) {

        String message = resolveConfirmMessage();

        publishAiReply(
                roomId,
                message,
                null,
                MessageType.CONFIRM
        );
    }

    @Transactional
    public void publishCancelReply(Long roomId) {

        String message = resolveCancelMessage();

        publishAiReply(
                roomId,
                message,
                null,
                MessageType.CANCEL
        );
    }

    @Transactional
    public void publishEditFailedReply(Long roomId, Exception exception) {

        String message = resolveEditFailedMessage(exception);

        publishAiReply(
                roomId,
                message,
                null,
                MessageType.TALK
        );
    }

    @Transactional
    public void publishAiGreetingIfNeeded(
            Long roomId,
            String username
    ) {

        if (chatRoomQueryService
                .findTravelIdByRoomId(roomId)
                .isEmpty()) {
            return;
        }

        if (existsAnyMessage(roomId)) {
            return;
        }

        User participant = userQueryService.findByUsername(username);
        User aiUser = userQueryService.findByUsername(SystemAccountConstants.AI_BOT_USERNAME);

        List<String> greetingMessages = resolveGreetingMessages(
                participant.getNickname(),
                aiUser.getNickname()
        );

        for (String message : greetingMessages) {
            publishAiReply(
                    roomId,
                    message,
                    null,
                    MessageType.TALK
            );
        }
    }

    public void publishSystemMessage(
            Long roomId,
            String username,
            MessageType messageType
    ) {

        User participant = userQueryService.findByUsername(username);
        String systemMessage = createSystemMessage(messageType, participant.getNickname());

        SendChatMessageResponse response = new SendChatMessageResponse(
                messageType,
                roomId,
                participant.getId(),
                participant.getNickname(),
                systemMessage,
                null,
                Instant.now()
        );

        publishMessage(roomId, response);
    }


    // 채팅방에 채팅 게시하기
    public void publishMessage(Long id, SendChatMessageResponse response) {

        messagingTemplate
                .convertAndSend(
                        "/sub/api/v1/chat/"+id,
                        response);
    }

    public ChatMessage createChatMessage(
            ChatRoom chatRoom,
            User sender,
            String message
    ) {

        return ChatMessage
                .builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .message(message)
                .sendAt(Instant.now())
                .build();
    }

    public SendChatMessageResponse makeChatResponse(
            Long roomId,
            User sender,
            ChatMessage chatMessage
    ) {

        return new SendChatMessageResponse(
                MessageType.TALK,
                roomId,
                sender.getId(),
                sender.getNickname(),
                chatMessage.getMessage(),
                null,
                chatMessage.getSendAt()
        );
    }

    public SendChatMessageResponse makeAiChatResponse(
            Long roomId,
            User sender,
            ChatMessage chatMessage,
            EditPlanPreviewResponse editPreview,
            MessageType type
    ) {

        return new SendChatMessageResponse(
                type,
                roomId,
                sender.getId(),
                sender.getNickname(),
                chatMessage.getMessage(),
                editPreview,
                chatMessage.getSendAt()
        );
    }

    // DB에 채팅 내역 저장
    public void saveMessage(ChatMessage chatMessage) {

        chatMessageRepository.save(chatMessage);
    }

    public String createSystemMessage(MessageType messageType, String userNickname) {

        return switch (messageType) {
            case ENTER -> userNickname + "님이 입장했습니다.";
            case LEAVE -> userNickname + "님이 퇴장했습니다.";
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 시스템 메시지 타입입니다."
            );
        };
    }

    // 편집 미리보기 결과 기준 AI 응답 컨텐츠 결정
    public AiReplyContent resolveAiReplyContent(EditPlanPreviewResponse preview) {

        String message =
                chatAiReplyMessageHelper.makeReplyMessage(preview);

        if (!preview
                .after()
                .processable()) {
            return new AiReplyContent(message, null);
        }

        return new AiReplyContent(message, preview);
    }

    // 채팅방 내 메시지 존재 여부 확인
    public boolean existsAnyMessage(Long roomId) {

        return chatMessageRepository
                .existsByChatRoom_IdAndDeletedFalse(roomId);
    }

    // 일정 수정 처리 실패 안내 메시지 조회
    public String resolveEditFailedMessage(Exception exception) {

        return chatAiReplyMessageHelper.makeEditFailedMessage(exception);
    }

    // 수정 확정(CONFIRM) 완료 메시지 조회
    public String resolveConfirmMessage() {

        return chatAiReplyMessageHelper.makeConfirmMessage();
    }

    // 수정 취소(CANCEL) 완료 메시지 조회
    public String resolveCancelMessage() {

        return chatAiReplyMessageHelper.makeCancelMessage();
    }

    // 채팅방 입장 AI 인사 메시지 목록 조회
    public List<String> resolveGreetingMessages(String userNickname, String aiNickname) {

        return chatAiReplyMessageHelper
                .makeGreetingMessages(userNickname, aiNickname);
    }

}
