package com.planb.query.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.planb.domain.chat.entity.ChatRoomMember;
import com.planb.global.config.exception.WebSocketExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.query.chat.repository.ChatRoomMemberQueryRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRoomMemberQueryService {

    private final ChatRoomMemberQueryRepository chatRoomMemberQueryRepository;

    public void deleteAllChatRoomMemberByRoomId(Long roomId){

        chatRoomMemberQueryRepository.deleteAllChatMemberByRoomId(roomId);
    }

    public List<ChatRoomMember> findByRoomId(Long roomId){

        return chatRoomMemberQueryRepository.findByRoomId(roomId);
    }

    public ChatRoomMember findByUserId(Long roomId,
                                       Long memberId){

        return chatRoomMemberQueryRepository
                .findByUserIdWithRoomId(roomId,memberId)
                .orElseThrow(()-> new BaseException(WebSocketExceptionEnum
                        .SUBSCRIBER_NOT_MATCHED));
    }

    public void validateDuplicateMemberWithRoom(Long roomId, Long userId){

        if(chatRoomMemberQueryRepository.checkSubscriberWithRoomId(roomId,userId)){
            throw new BaseException(WebSocketExceptionEnum.USER_ROOM_DUPLICATED);
        }
    }

    // 단일 메소드 (facade사용 금지)
    @Transactional(readOnly = true)
    public boolean checkSubscriberWithRoomId(Long roomId,Long userId){

        return chatRoomMemberQueryRepository.checkSubscriberWithRoomId(roomId,userId);

    }
}
