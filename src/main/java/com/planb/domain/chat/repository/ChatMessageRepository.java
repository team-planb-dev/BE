package com.planb.domain.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.planb.domain.chat.entity.ChatMessage;

@Repository
public interface ChatMessageRepository
        extends JpaRepository<ChatMessage,Long> {

    // 채팅방 내 삭제되지 않은 메시지 존재 여부
    boolean existsByChatRoom_IdAndDeletedFalse(Long chatRoomId);
}
