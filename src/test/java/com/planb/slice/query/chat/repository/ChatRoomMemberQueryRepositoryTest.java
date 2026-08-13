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
import com.planb.domain.chat.entity.ChatRoomMember;
import com.planb.domain.user.entity.User;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.chat.repository.ChatRoomMemberQueryRepository;
import com.planb.slice.query.chat.repository.helper.ChatDomainRepositoryTestHelper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        ChatRoomMemberQueryRepository.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class ChatRoomMemberQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ChatRoomMemberQueryRepository chatRoomMemberQueryRepository;

    private ChatDomainRepositoryTestHelper helper;

    @BeforeEach
    void setUp() {

        helper = new ChatDomainRepositoryTestHelper(entityManager);
    }

    @Test
    @DisplayName("채팅방 id를 통해 해당 채팅방의 모든 회원을 삭제")
    void deleteAllChatMemberByRoomId() {

        // given
        User user1 = helper.createUser(
                "user1@example.com",
                "test1234!",
                "ROLE_USER",
                "nickname1",
                false
        );

        User user2 = helper.createUser(
                "user2@exmaple.com",
                "test1234!",
                "ROLE_USER",
                "nickname2",
                false
        );

        User user3 = helper.createUser(
                "user3@example.com",
                "test1234!",
                "ROLE_USER",
                "nickname3",
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

        ChatRoomMember room1Member1 = helper.createChatRoomMember(
                room1,
                user1
        );

        ChatRoomMember room1Member2 = helper.createChatRoomMember(
                room1,
                user2
        );

        ChatRoomMember room2Member = helper.createChatRoomMember(
                room2,
                user3
        );

        Long room1Member1Id = room1Member1.getId();
        Long room1Member2Id = room1Member2.getId();
        Long room2MemberId = room2Member.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        chatRoomMemberQueryRepository
                .deleteAllChatMemberByRoomId(room1.getId());

        entityManager.clear();

        // then
        ChatRoomMember deletedMember1 = entityManager.find(
                ChatRoomMember.class,
                room1Member1Id
        );

        ChatRoomMember deletedMember2 = entityManager.find(
                ChatRoomMember.class,
                room1Member2Id
        );

        ChatRoomMember remainingMember = entityManager.find(
                ChatRoomMember.class,
                room2MemberId
        );

        assertThat(deletedMember1)
                .isNull();

        assertThat(deletedMember2)
                .isNull();

        assertThat(remainingMember)
                .isNotNull();

        assertThat(remainingMember
                .getChatRoom()
                .getId())
                .isEqualTo(room2.getId());
    }

    @Test
    @DisplayName("채팅방 id를 통해 해당 채팅방에 참여한 회원 목록 조회")
    void findByRoomIdSuccess() {

        // given
        User user1 = helper.createUser(
                "user1@example.com",
                "test1234!",
                "ROLE_USER",
                "nickname1",
                false
        );

        User user2 = helper.createUser(
                "user2@exmaple.com",
                "test1234!",
                "ROLE_USER",
                "nickname2",
                false
        );

        User otherRoomUser = helper.createUser(
                "user3@exmaple.ecom",
                "test1234!",
                "ROLE_USER",
                "nickname3",
                false
        );

        ChatRoom targetRoom = helper.createChatRoom(
                "targetRoom",
                false
        );

        ChatRoom otherRoom = helper.createChatRoom(
                "otherRoom",
                false
        );

        ChatRoomMember targetMember1 = helper.createChatRoomMember(
                targetRoom,
                user1
        );

        ChatRoomMember targetMember2 = helper.createChatRoomMember(
                targetRoom,
                user2
        );

        helper.createChatRoomMember(
                otherRoom,
                otherRoomUser
        );

        Long targetRoomId = targetRoom.getId();
        Long targetMember1Id = targetMember1.getId();
        Long targetMember2Id = targetMember2.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        List<ChatRoomMember> result = chatRoomMemberQueryRepository
                .findByRoomId(targetRoomId);

        // then
        assertThat(result)
                .hasSize(2);

        assertThat(result)
                .extracting(ChatRoomMember::getId)
                .containsExactlyInAnyOrder(
                        targetMember1Id,
                        targetMember2Id
                );

        assertThat(result)
                .allSatisfy(member ->
                        assertThat(member
                                .getChatRoom()
                                .getId())
                                .isEqualTo(targetRoomId)
                );
    }

    @Test
    @DisplayName("회원이 없는 채팅방 id로 조회하면 빈 목록을 반환")
    void findByRoomIdEmpty() {

        // given
        ChatRoom emptyRoom = helper.createChatRoom(
                "emptyRoom",
                false
        );

        Long emptyRoomId = emptyRoom.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        List<ChatRoomMember> result = chatRoomMemberQueryRepository
                .findByRoomId(emptyRoomId);

        // then
        assertThat(result)
                .isEmpty();
    }

    @Test
    @DisplayName("채팅방 id와 사용자 id를 통해 채팅방 멤버 조회 성공")
    void findByUserIdWithRoomIdSuccess() {

        // given
        User targetUser = helper.createUser(
                "targetUser@example.com",
                "test1234!",
                "ROLE_USER",
                "targetNickname",
                false
        );

        User otherUser = helper.createUser(
                "otherUser@example.com",
                "test1234!",
                "ROLE_USER",
                "otherNickname",
                false
        );

        ChatRoom targetRoom = helper.createChatRoom(
                "targetRoom",
                false
        );

        ChatRoom otherRoom = helper.createChatRoom(
                "otherRoom",
                false
        );

        ChatRoomMember targetMember = helper.createChatRoomMember(
                targetRoom,
                targetUser
        );

        helper.createChatRoomMember(
                otherRoom,
                otherUser
        );

        Long targetRoomId = targetRoom.getId();
        Long targetUserId = targetUser.getId();
        Long targetMemberId = targetMember.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<ChatRoomMember> result =
                chatRoomMemberQueryRepository
                        .findByUserIdWithRoomId(
                                targetRoomId,
                                targetUserId
                        );

        // then
        assertThat(result)
                .isPresent();

        assertThat(result.get().getId())
                .isEqualTo(targetMemberId);

        assertThat(result.get()
                .getChatRoom()
                .getId())
                .isEqualTo(targetRoomId);

        assertThat(result.get()
                .getUser()
                .getId())
                .isEqualTo(targetUserId);
    }

    @Test
    @DisplayName("사용자가 다른 채팅방의 멤버이면 조회 결과가 비어 있음")
    void findByUserIdWithRoomIdDifferentRoom() {

        // given
        User user = helper.createUser(
                "user1@example.com",
                "test1234!",
                "ROLE_USER",
                "nickname1",
                false
        );

        ChatRoom joinedRoom = helper.createChatRoom(
                "joinedRoom",
                false
        );

        ChatRoom targetRoom = helper.createChatRoom(
                "targetRoom",
                false
        );

        helper.createChatRoomMember(
                joinedRoom,
                user
        );

        Long targetRoomId = targetRoom.getId();
        Long userId = user.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        Optional<ChatRoomMember> result =
                chatRoomMemberQueryRepository
                        .findByUserIdWithRoomId(
                                targetRoomId,
                                userId
                        );

        // then
        assertThat(result)
                .isEmpty();
    }

    @Test
    @DisplayName("사용자가 해당 채팅방의 참여자일 경우, true를 반환")
    void checkSubscriberWithRoomIdTrue() {

        // given
        User user = helper.createUser(
                "user1@example.com",
                "test1234!",
                "ROLE_USER",
                "nickname1",
                false
        );

        ChatRoom chatRoom = helper.createChatRoom(
                "chatRoom",
                false
        );

        helper.createChatRoomMember(
                chatRoom,
                user
        );

        Long chatRoomId = chatRoom.getId();
        Long userId = user.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        boolean result = chatRoomMemberQueryRepository
                .checkSubscriberWithRoomId(
                        chatRoomId,
                        userId
                );

        // then
        assertThat(result)
                .isTrue();
    }

    @Test
    @DisplayName("사용자가 해당 채팅방의 참여자가 아닐 경우, false를 반환")
    void checkSubscriberWithRoomIdFalse() {

        // given
        User member = helper.createUser(
                "member@example.com",
                "test1234!",
                "ROLE_USER",
                "memberNickname",
                false
        );

        User nonMember = helper.createUser(
                "nonMember@example.com",
                "test1234!",
                "ROLE_USER",
                "nonMemberNickname",
                false
        );

        ChatRoom chatRoom = helper.createChatRoom(
                "chatRoom",
                false
        );

        helper.createChatRoomMember(
                chatRoom,
                member
        );

        Long chatRoomId = chatRoom.getId();
        Long nonMemberId = nonMember.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        boolean result = chatRoomMemberQueryRepository
                .checkSubscriberWithRoomId(
                        chatRoomId,
                        nonMemberId
                );

        // then
        assertThat(result)
                .isFalse();
    }

    @Test
    @DisplayName("사용자가 다른 채팅방의 참여자인 경우, false를 반환")
    void checkSubscriberWithRoomIdDifferentRoom() {

        // given
        User user = helper.createUser(
                "user1@example.com",
                "test1234!",
                "ROLE_USER",
                "nickname1",
                false
        );

        ChatRoom joinedRoom = helper.createChatRoom(
                "joinedRoom",
                false
        );

        ChatRoom otherRoom = helper.createChatRoom(
                "otherRoom",
                false
        );

        helper.createChatRoomMember(
                joinedRoom,
                user
        );

        Long otherRoomId = otherRoom.getId();
        Long userId = user.getId();

        entityManager.flush();
        entityManager.clear();

        // when
        boolean result = chatRoomMemberQueryRepository
                .checkSubscriberWithRoomId(
                        otherRoomId,
                        userId
                );

        // then
        assertThat(result)
                .isFalse();
    }
}