package com.planb.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.response.SendChatMessageResponse;
import com.planb.domain.chat.entity.ChatMessage;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.chat.repository.ChatMessageRepository;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import com.planb.domain.user.entity.User;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ChatMessageService {

    private final ChatMessageRepository chatMessageRepository;
    private final SimpMessagingTemplate messagingTemplate;


    // 채팅방에 채팅 게시하기
    public void publishMessage(Long id, SendChatMessageResponse response){

        messagingTemplate
                .convertAndSend(
                        "/sub/api/v1/chat/"+id,
                        response);
    }

    public ChatMessage createChatMessage
            (ChatRoom chatRoom,
             User sender,
             String message){

        return ChatMessage
                .builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .message(message)
                .sendAt(Instant.now())
                .build();
    }

    public SendChatMessageResponse makeChatResponse
            (Long roomId,
             User sender,
             ChatMessage chatMessage){

        return new SendChatMessageResponse(
                MessageType.TALK,
                roomId,
                sender.getId(),
                sender.getNickname(),
                chatMessage.getMessage(),
                chatMessage.getSendAt()
        );
    }

    // DB에 채팅 내역 저장
    public void saveMessage(ChatMessage chatMessage){

        chatMessageRepository.save(chatMessage);
    }

    public String createSystemMessage(MessageType messageType,String userNickname){

        return switch (messageType){
            case ENTER -> userNickname + "님이 입장했습니다.";
            case LEAVE -> userNickname + "님이 퇴장했습니다.";
            default -> throw new IllegalArgumentException(
                    "지원하지 않는 시스템 메시지 타입입니다."
            );
        };
    }




}
