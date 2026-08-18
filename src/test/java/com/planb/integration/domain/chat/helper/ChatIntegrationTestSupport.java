package com.planb.integration.domain.chat.helper;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import com.planb.domain.chat.dto.request.AddChatRoomMemberRequest;
import com.planb.domain.chat.dto.request.CreateChatRoomRequest;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.global.security.dto.request.LoginRequest;
import com.planb.integration.IntegrationTest;
import com.planb.integration.domain.user.dto.LoginResult;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat 통합 테스트 공통 지원 클래스.
 * 사용자 생성, 로그인, 사용자 조회,
 * 채팅방 생성 및 멤버 추가에 필요한 공통 헬퍼 메소드를 제공한다.
 */
public abstract class ChatIntegrationTestSupport
        extends IntegrationTest {

    protected static final String CREATE_USER_URL =
            "/api/v1/user/create";

    protected static final String LOGIN_URL =
            "/login";

    protected static final String USER_ME_URL =
            "/api/v1/user/me";

    protected static final String CREATE_CHAT_ROOM_URL =
            "/api/v1/chat/room/create";

    protected static final String DELETE_CHAT_ROOM_URL =
            "/api/v1/chat/room/delete";

    protected static final String ADD_CHAT_MEMBER_URL =
            "/api/v1/chat/member/add";

    protected static final String DELETE_CHAT_MEMBER_URL =
            "/api/v1/chat/member/delete";

    protected static final String PASSWORD =
            "test1234!";

    protected static final String NICKNAME_PREFIX =
            "chatNickname-";

    @Autowired
    protected ObjectMapper objectMapper;

    protected TestUser createAuthenticatedUser()
            throws Exception {

        String uniqueValue =
                createUniqueValue();

        String username =
                "chat-user-" + uniqueValue + "@example.com";

        String nickname =
                NICKNAME_PREFIX + uniqueValue;

        createUser(
                username,
                nickname,
                PASSWORD
        );

        LoginResult loginResult =
                login(
                        username,
                        PASSWORD
                );

        Long userId =
                getUserId(
                        loginResult.accessToken()
                );

        return new TestUser(
                userId,
                username,
                nickname,
                loginResult.accessToken()
        );
    }

    protected void createUser(
            String username,
            String nickname,
            String password
    ) throws Exception {

        UserCreateRequest request =
                new UserCreateRequest(
                        username,
                        nickname,
                        password,
                        true,
                        true,
                        true
                );

        mockMvc.perform(
                        post(CREATE_USER_URL)
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
                        jsonPath("$.data.username")
                                .value(username)
                );
    }

    protected LoginResult login(
            String username,
            String password
    ) throws Exception {

        LoginRequest request =
                new LoginRequest(
                        username,
                        password
                );

        MvcResult result =
                mockMvc.perform(
                                post(LOGIN_URL)
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
                                        .value(username)
                        )
                        .andExpect(
                                header().string(
                                        "Authorization",
                                        startsWith("Bearer ")
                                )
                        )
                        .andExpect(
                                cookie().exists(
                                        "refreshToken"
                                )
                        )
                        .andReturn();

        String accessToken =
                result.getResponse()
                        .getHeader("Authorization");

        Cookie refreshTokenCookie =
                result.getResponse()
                        .getCookie("refreshToken");

        assertThat(accessToken)
                .isNotBlank()
                .startsWith("Bearer ");

        assertThat(refreshTokenCookie)
                .isNotNull();

        return new LoginResult(
                accessToken,
                refreshTokenCookie
        );
    }

    protected Long getUserId(
            String accessToken
    ) throws Exception {

        MvcResult result =
                mockMvc.perform(
                                get(USER_ME_URL)
                                        .header(
                                                "Authorization",
                                                accessToken
                                        )
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.success")
                                        .value(true)
                        )
                        .andExpect(
                                jsonPath("$.data.userId")
                                        .isNumber()
                        )
                        .andReturn();

        Number userId =
                JsonPath.read(
                        result.getResponse()
                                .getContentAsString(),
                        "$.data.userId"
                );

        return userId.longValue();
    }

    protected Long createChatRoom(
            String accessToken,
            String chatRoomName
    ) throws Exception {

        CreateChatRoomRequest request =
                new CreateChatRoomRequest(
                        chatRoomName
                );

        MvcResult result =
                mockMvc.perform(
                                post(CREATE_CHAT_ROOM_URL)
                                        .header(
                                                "Authorization",
                                                accessToken
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
                        .andReturn();

        Number roomId =
                JsonPath.read(
                        result.getResponse()
                                .getContentAsString(),
                        "$.data.chatRoomId"
                );

        return roomId.longValue();
    }

    protected void addChatRoomMember(
            String accessToken,
            Long roomId,
            Long userId
    ) throws Exception {

        AddChatRoomMemberRequest request =
                new AddChatRoomMemberRequest(
                        roomId,
                        userId
                );

        mockMvc.perform(
                        post(ADD_CHAT_MEMBER_URL)
                                .header(
                                        "Authorization",
                                        accessToken
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
                );
    }

    protected String createUniqueValue() {

        return UUID.randomUUID()
                .toString()
                .substring(0, 8);
    }

    protected record TestUser(
            Long userId,
            String username,
            String nickname,
            String accessToken
    ) {
    }
}