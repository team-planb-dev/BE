package com.planb.integration.domain.chat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import com.planb.domain.chat.dto.request.AddChatRoomMemberRequest;
import com.planb.domain.chat.dto.request.CreateChatRoomRequest;
import com.planb.domain.chat.dto.request.DeleteChatRoomMemberRequest;
import com.planb.domain.chat.dto.request.DeleteChatRoomRequest;
import com.planb.integration.domain.chat.helper.ChatIntegrationTestSupport;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat REST API 통합 테스트.
 * 채팅방 생성, 채팅방 멤버 등록 및 삭제,
 * 채팅방 삭제 기능을 검증한다.
 */
public class ChatRestIntegrationTest
        extends ChatIntegrationTestSupport {

    @Test
    @DisplayName("로그인한 사용자가 채팅방 생성 성공")
    void createChatRoomSuccess() throws Exception {

        // given
        TestUser testUser =
                createAuthenticatedUser();

        String chatRoomName =
                "chat-room-" + createUniqueValue();

        CreateChatRoomRequest request =
                new CreateChatRoomRequest(
                        chatRoomName
                );

        // when & then
        mockMvc.perform(
                        post(CREATE_CHAT_ROOM_URL)
                                .header(
                                        "Authorization",
                                        testUser.accessToken()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.chatRoomId")
                                .isNumber()
                )
                .andExpect(
                        jsonPath("$.data.chatRoomName")
                                .value(chatRoomName)
                )
                .andExpect(
                        jsonPath("$.data.createdAt")
                                .exists()
                )
                .andExpect(
                        jsonPath("$.data.message")
                                .value(
                                        "채팅방이 생성되었습니다."
                                )
                )
                .andExpect(
                        jsonPath("$.error")
                                .isEmpty()
                );
    }

    @Test
    @DisplayName("Access Token 없이 채팅방 생성 시 인증 실패")
    void createChatRoomUnauthorized() throws Exception {

        // given
        CreateChatRoomRequest request =
                new CreateChatRoomRequest(
                        "unauthorized-room"
                );

        // when & then
        mockMvc.perform(
                        post(CREATE_CHAT_ROOM_URL)
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("채팅방 생성 후 사용자를 채팅방 멤버로 추가 성공")
    void addChatRoomMemberSuccess() throws Exception {

        // given
        TestUser testUser =
                createAuthenticatedUser();

        Long roomId =
                createChatRoom(
                        testUser.accessToken(),
                        "member-add-room-"
                                + createUniqueValue()
                );

        AddChatRoomMemberRequest request =
                new AddChatRoomMemberRequest(
                        roomId,
                        testUser.userId()
                );

        // when & then
        mockMvc.perform(
                        post(ADD_CHAT_MEMBER_URL)
                                .header(
                                        "Authorization",
                                        testUser.accessToken()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.username")
                                .value(testUser.username())
                )
                .andExpect(
                        jsonPath("$.data.chatRoomId")
                                .value(roomId)
                )
                .andExpect(
                        jsonPath("$.data.message")
                                .value(
                                        testUser.username()
                                                + "님이 채팅방에 입장하셨습니다."
                                )
                )
                .andExpect(
                        jsonPath("$.error")
                                .isEmpty()
                );
    }

    @Test
    @DisplayName("이미 참여한 사용자를 같은 채팅방에 추가하면 중복 예외")
    void addDuplicateChatRoomMemberFail() throws Exception {

        // given
        TestUser testUser =
                createAuthenticatedUser();

        Long roomId =
                createChatRoom(
                        testUser.accessToken(),
                        "duplicate-room-"
                                + createUniqueValue()
                );

        addChatRoomMember(
                testUser.accessToken(),
                roomId,
                testUser.userId()
        );

        AddChatRoomMemberRequest request =
                new AddChatRoomMemberRequest(
                        roomId,
                        testUser.userId()
                );

        // when & then
        mockMvc.perform(
                        post(ADD_CHAT_MEMBER_URL)
                                .header(
                                        "Authorization",
                                        testUser.accessToken()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                /*
                 * 현재 ApiExceptionHandler의 BaseException 처리 메소드에
                 * @ResponseStatus가 없어서 HTTP 상태는 200으로 반환된다.
                 */
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.data")
                                .isEmpty()
                )
                .andExpect(
                        jsonPath("$.error.errorCode")
                                .value(
                                        "WEBSOCKET.EXCEPTION.USER_ROOM_DUPLICATED"
                                )
                )
                .andExpect(
                        jsonPath("$.error.message")
                                .value(
                                        "해당 유저는 이미 해당 방과 등록 되어있습니다."
                                )
                );
    }

    @Test
    @DisplayName("채팅방에 참여한 사용자 삭제 성공")
    void deleteChatRoomMemberSuccess() throws Exception {

        // given
        TestUser testUser =
                createAuthenticatedUser();

        Long roomId =
                createChatRoom(
                        testUser.accessToken(),
                        "member-delete-room-"
                                + createUniqueValue()
                );

        addChatRoomMember(
                testUser.accessToken(),
                roomId,
                testUser.userId()
        );

        DeleteChatRoomMemberRequest request =
                new DeleteChatRoomMemberRequest(
                        roomId,
                        testUser.userId()
                );

        // when & then
        mockMvc.perform(
                        delete(DELETE_CHAT_MEMBER_URL)
                                .header(
                                        "Authorization",
                                        testUser.accessToken()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.chatRoomId")
                                .value(roomId)
                )
                .andExpect(
                        jsonPath("$.data.username")
                                .value(testUser.username())
                )
                .andExpect(
                        jsonPath("$.data.message")
                                .value(
                                        testUser.username()
                                                + "님이 퇴장하셨습니다."
                                )
                )
                .andExpect(
                        jsonPath("$.error")
                                .isEmpty()
                );
    }

    @Test
    @DisplayName("채팅방 삭제 시 채팅방과 멤버 관계 삭제 성공")
    void deleteChatRoomSuccess() throws Exception {

        // given
        TestUser testUser =
                createAuthenticatedUser();

        String chatRoomName =
                "delete-room-"
                        + createUniqueValue();

        Long roomId =
                createChatRoom(
                        testUser.accessToken(),
                        chatRoomName
                );

        addChatRoomMember(
                testUser.accessToken(),
                roomId,
                testUser.userId()
        );

        DeleteChatRoomRequest request =
                new DeleteChatRoomRequest(
                        roomId
                );

        // when & then
        mockMvc.perform(
                        delete(DELETE_CHAT_ROOM_URL)
                                .header(
                                        "Authorization",
                                        testUser.accessToken()
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.chatRoomId")
                                .value(roomId)
                )
                .andExpect(
                        jsonPath("$.data.chatRoomName")
                                .value(chatRoomName)
                )
                .andExpect(
                        jsonPath(
                                "$.data.chatRoomDescription"
                        )
                                .value(
                                        "삭제 보관된 메시지 갯수: 0"
                                )
                )
                .andExpect(
                        jsonPath("$.data.message")
                                .value(
                                        "채팅방이 삭제되었습니다."
                                )
                )
                .andExpect(
                        jsonPath("$.error")
                                .isEmpty()
                );
    }
}