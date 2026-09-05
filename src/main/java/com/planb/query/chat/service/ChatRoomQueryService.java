package com.planb.query.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.global.config.exception.WebSocketExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.query.chat.repository.ChatRoomQueryRepository;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ChatRoomQueryService {

    private final ChatRoomQueryRepository chatRoomQueryRepository;

    // roomId 기준으로 채팅방 찾기
    public ChatRoom findChatRoomByRoomId(Long roomId){

        return chatRoomQueryRepository
                .findByRoomId(roomId)
                .orElseThrow(()->
                        new BaseException(WebSocketExceptionEnum
                                .CHATROOM_NOT_FOUND));
    }

    // travelId 기준으로 채팅방 찾기
    public Optional<ChatRoom> findChatRoomByTravelId(Long travelId){

        return chatRoomQueryRepository
                .findByTravelId(travelId);
    }

}
