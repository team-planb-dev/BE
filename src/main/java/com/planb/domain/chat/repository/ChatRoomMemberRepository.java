package com.planb.domain.chat.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.planb.domain.chat.entity.ChatRoomMember;

@Repository
public interface ChatRoomMemberRepository
        extends JpaRepository<ChatRoomMember,Long> {
}
