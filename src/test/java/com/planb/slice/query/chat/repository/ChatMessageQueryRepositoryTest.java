package com.planb.slice.query.chat.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.planb.domain.chat.entity.ChatMessage;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.user.entity.User;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.chat.repository.ChatMessageQueryRepository;
import com.planb.slice.query.chat.repository.helper.ChatDomainRepositoryTestHelper;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        ChatMessageQueryRepository.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class ChatMessageQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatMessageQueryRepository chatMessageQueryRepository;

    private ChatDomainRepositoryTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new ChatDomainRepositoryTestHelper(entityManager);
    }

    @Test
    @DisplayName("채팅방에 포함된 모든 메시지를 Soft Delete 처리")
    void softDeleteAllMessageInChatRoom() {

        User sender = helper.createUser(
                "user1",
                "password",
                "ROLE_USER",
                "nickname1",
                false
        );

        ChatRoom chatRoom = helper.createChatRoom(
                "채팅방",
                false
        );

        ChatMessage message1 = helper.createChatMessage(
                chatRoom,
                sender,
                "message1",
                Instant.now(),
                false
        );

        ChatMessage message2 = helper.createChatMessage(
                chatRoom,
                sender,
                "message2",
                Instant.now(),
                false
        );

        entityManager.flush();
        entityManager.clear();

        long updateCount = chatMessageQueryRepository
                .softDeleteAllMessageInChatRoom(chatRoom.getId());

        entityManager.clear();

        ChatMessage deletedMessage1 = entityManager.find(
                ChatMessage.class,
                message1.getId()
        );

        ChatMessage deletedMessage2 = entityManager.find(
                ChatMessage.class,
                message2.getId()
        );

        assertThat(updateCount).isEqualTo(2);
        assertThat(deletedMessage1.isDeleted()).isTrue();
        assertThat(deletedMessage2.isDeleted()).isTrue();
    }

    @Test
    @DisplayName("다른 채팅방의 메시지는 Soft Delete 시행X")
    void softDeleteAllMessageInChatRoomDoesNotDeleteOtherRoomMessage() {

        User sender = helper.createUser(
                "user1",
                "password",
                "ROLE_USER",
                "nickname1",
                false
        );

        ChatRoom room1 = helper.createChatRoom(
                "room1",
                false
        );

        ChatRoom room2 = helper.createChatRoom(
                "room2",
                false
        );

        ChatMessage room1Message = helper.createChatMessage(
                room1,
                sender,
                "room1 message",
                Instant.now(),
                false
        );

        ChatMessage room2Message = helper.createChatMessage(
                room2,
                sender,
                "room2 message",
                Instant.now(),
                false
        );

        entityManager.flush();
        entityManager.clear();

        long updateCount = chatMessageQueryRepository
                .softDeleteAllMessageInChatRoom(room1.getId());

        entityManager.clear();

        ChatMessage deletedMessage = entityManager.find(
                ChatMessage.class,
                room1Message.getId()
        );

        ChatMessage notDeletedMessage = entityManager.find(
                ChatMessage.class,
                room2Message.getId()
        );

        assertThat(updateCount).isEqualTo(1);
        assertThat(deletedMessage.isDeleted()).isTrue();
        assertThat(notDeletedMessage.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("이미 삭제된 메시지는 Soft Delete 대상에서 제외")
    void softDeleteAllMessageInChatRoomExcludesDeletedMessage() {

        User sender = helper.createUser(
                "user1",
                "password",
                "ROLE_USER",
                "nickname1",
                false
        );

        ChatRoom chatRoom = helper.createChatRoom(
                "채팅방",
                false
        );

        ChatMessage deletedMessage = helper.createChatMessage(
                chatRoom,
                sender,
                "deleted message",
                Instant.now(),
                true
        );

        ChatMessage activeMessage = helper.createChatMessage(
                chatRoom,
                sender,
                "active message",
                Instant.now(),
                false
        );

        entityManager.flush();
        entityManager.clear();

        long updateCount = chatMessageQueryRepository
                .softDeleteAllMessageInChatRoom(chatRoom.getId());

        entityManager.clear();

        ChatMessage alreadyDeleted = entityManager.find(
                ChatMessage.class,
                deletedMessage.getId()
        );

        ChatMessage newlyDeleted = entityManager.find(
                ChatMessage.class,
                activeMessage.getId()
        );

        assertThat(updateCount).isEqualTo(1);
        assertThat(alreadyDeleted.isDeleted()).isTrue();
        assertThat(newlyDeleted.isDeleted()).isTrue();
    }
}