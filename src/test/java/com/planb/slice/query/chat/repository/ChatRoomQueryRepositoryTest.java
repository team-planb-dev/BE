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

import com.planb.domain.chat.entity.ChatRoom;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.chat.repository.ChatRoomQueryRepository;
import com.planb.slice.query.chat.repository.helper.ChatDomainRepositoryTestHelper;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        ChatRoomQueryRepository.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class ChatRoomQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatRoomQueryRepository chatRoomQueryRepository;

    private ChatDomainRepositoryTestHelper helper;

    @BeforeEach
    void setUp() {
        helper = new ChatDomainRepositoryTestHelper(entityManager);
    }

    @Test
    @DisplayName("채팅방 id로 채팅방 조회 성공")
    void findByRoomId_success() {

        // given
        ChatRoom chatRoom = helper.createChatRoom(
                "testRoom",
                false
        );

        Long chatRoomId = chatRoom.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<ChatRoom> result = chatRoomQueryRepository
                .findByRoomId(chatRoomId);

        // then
        assertThat(result).isPresent();

        assertThat(result
                .get()
                .getId())
                .isEqualTo(chatRoomId);

        assertThat(result
                .get()
                .getChatRoomName())
                .isEqualTo("testRoom");

        assertThat(result
                .get()
                .isDeleted())
                .isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 채팅방 id 조회 시, Optional.empty 반환")
    void findByRoomId_failure() {

        // given
        Long unknownRoomId = Long.MAX_VALUE;

        // when
        Optional<ChatRoom> result = chatRoomQueryRepository
                .findByRoomId(unknownRoomId);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("삭제된 채팅방은 조회x")
    void findByRoomId_deletedRoom() {

        // given
        ChatRoom deletedRoom = helper.createChatRoom(
                "deletedRoom",
                true
        );

        Long deletedRoomId = deletedRoom.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<ChatRoom> result = chatRoomQueryRepository
                .findByRoomId(deletedRoomId);

        // then
        assertThat(result).isEmpty();
    }
}