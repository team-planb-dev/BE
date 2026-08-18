package com.planb.slice.query.chat.repository.helper;


import com.planb.domain.user.entity.TermsAgreement;
import lombok.RequiredArgsConstructor;

import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import com.planb.domain.chat.entity.ChatMessage;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.chat.entity.ChatRoomMember;
import com.planb.domain.user.entity.User;

import java.time.Instant;

@RequiredArgsConstructor
public class ChatDomainRepositoryTestHelper {

    private final TestEntityManager entityManager;

    public User createUser
            (String username,
             String password,
             String role,
             String nickname,
             boolean deleted) {

        User user = User
                .builder()
                .username(username)
                .password(password)
                .role(role)
                .termsAgreement(
                        new TermsAgreement(
                                true,
                                true,
                                true))
                .nickname(nickname)
                .deleted(deleted)
                .build();

        return entityManager
                .persist(user);
    }

    // ChatRoom 생성
    public ChatRoom createChatRoom
    (String chatRoomName,
     boolean deleted) {

        ChatRoom chatRoom = ChatRoom
                .builder()
                .chatRoomName(chatRoomName)
                .deleted(deleted)
                .build();

        return entityManager
                .persist(chatRoom);
    }

    // ChatMessage 생성
    public ChatMessage createChatMessage
    (ChatRoom chatRoom,
     User sender,
     String message,
     Instant sendAt,
     boolean deleted) {

        ChatMessage chatMessage = ChatMessage
                .builder()
                .chatRoom(chatRoom)
                .sender(sender)
                .message(message)
                .sendAt(sendAt)
                .deleted(deleted)
                .build();

        return entityManager
                .persist(chatMessage);
    }

    public ChatRoomMember createChatRoomMember
            (ChatRoom chatRoom,
             User user) {

        ChatRoomMember chatRoomMember = ChatRoomMember
                .builder()
                .chatRoom(chatRoom)
                .user(user)
                .build();

        return entityManager
                .persist(chatRoomMember);
    }
}
