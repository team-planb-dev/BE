package com.planb.query.chat.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.woojukang.springChatPractice.domain.chat.entity.QChatMessage;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class ChatMessageQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final EntityManager entityManager;
    private final QChatMessage chatMessage = QChatMessage.chatMessage;


    // 삭제된 chatRoom에 연결된 모든 메시지 삭제(soft)
    public Long softDeleteAllMessageInChatRoom(Long RoomId){

         Long updatedCount = jpaQueryFactory
                 .update(chatMessage)
                 .set(chatMessage
                         .deleted,
                         true)
                 .set(chatMessage
                         .deletedAt,
                         Instant.now())
                 .where(chatMessage
                                 .chatRoom
                                 .id
                                 .eq(RoomId),
                         chatMessage
                                 .deleted
                                 .isFalse())
                 .execute();

         entityManager.clear();

         return updatedCount;
    }

}
