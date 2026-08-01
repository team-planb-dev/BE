package com.planb.query.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.query.chat.repository.ChatMessageQueryRepository;

@Service
@RequiredArgsConstructor
public class ChatMessageQueryService {

    private final ChatMessageQueryRepository chatMessageQueryRepository;

    public Long softDeleteAllMessageInChatRoom(Long roomId){

        return chatMessageQueryRepository.softDeleteAllMessageInChatRoom(roomId);
    }
}
