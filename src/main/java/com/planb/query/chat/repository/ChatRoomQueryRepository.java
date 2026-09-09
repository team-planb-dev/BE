package com.planb.query.chat.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.chat.entity.QChatRoom;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ChatRoomQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final QChatRoom chatRoom = QChatRoom.chatRoom;

    public Optional<ChatRoom> findByRoomId(Long roomId){

        return Optional.ofNullable(jpaQueryFactory
                .selectFrom(chatRoom)
                .where(
                        chatRoom
                                .id
                                .eq(roomId),
                        chatRoom
                                .deleted
                                .isFalse())
                .fetchOne());
    }

    public Optional<ChatRoom> findByTravelId(Long travelId){

        return Optional.ofNullable(jpaQueryFactory
                .selectFrom(chatRoom)
                .where(
                        chatRoom
                                .travel
                                .id
                                .eq(travelId),
                        chatRoom
                                .deleted
                                .isFalse())
                .fetchOne());
    }

}
