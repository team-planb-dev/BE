package com.planb.domain.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.planb.domain.chat.entity.ChatMessage;

@Repository
public interface ChatMessageRepository
        extends JpaRepository<ChatMessage,Long> {
}
