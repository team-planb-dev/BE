package com.planb.integration.domain.user;


import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

import com.planb.integration.IntegrationTest;
import com.planb.integration.domain.user.dto.LoginResult;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.global.security.dto.request.LoginRequest;


import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

import static org.hamcrest.Matchers.startsWith;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * User API 통합 테스트
 * 회원 생성, 조회, 삭제 기능과 예외 상황 검증
 */
public class UserIntegrationTest extends IntegrationTest {

    /*
    API 호출 URL 모음
     */
    private static final String CREATE_USER_URL =
            "/api/v1/user/create";

    private static final String LOGIN_URL =
            "/login";

    private static final String USER_ME_URL =
            "/api/v1/user/me";

    private static final String DELETE_USER_URL =
            "/api/v1/user/delete";

    private static final String CHECK_USERNAME_DUPLICATION_URL =
            "/api/v1/user/check/duplication/username";

    private static final String CHECK_NICKNAME_DUPLICATION_URL =
            "/api/v1/user/check/duplication/nickname";

    // User 테스트 객체 password 필드
    private static final String NICKNAME =
            "testNickname";
    private static final String PASSWORD =
            "test1234!";

    @Autowired
    private ObjectMapper objectMapper;


    @Test
    @DisplayName("회원가입 성공")
    void createUserSuccess() throws Exception {

        // given
        String username = createUniqueUsername();

        UserCreateRequest request =
                new UserCreateRequest(username,
                        NICKNAME,
                        PASSWORD,
                        true,
                        true,
                        true);

        // when & then
        mockMvc.perform(post(CREATE_USER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.updatedAt").exists())
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("회원가입 후 로그인 성공")
    void loginSuccess() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username,
                NICKNAME,
                PASSWORD);

        LoginRequest request =
                new LoginRequest(username,PASSWORD);

        // when
        MvcResult result = mockMvc
                .perform(post(LOGIN_URL)
                        .contentType(MediaType
                                .APPLICATION_JSON)
                        .content(objectMapper
                                .writeValueAsString(request)))
                .andExpect(status()
                        .isOk())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andExpect(jsonPath("$.data.username")
                        .value(username))
                .andExpect(jsonPath("$.data.message")
                        .value("로그인에 성공하였습니다."))
                .andExpect(jsonPath("$.data.loginAt")
                        .exists())
                .andExpect(jsonPath("$.error")
                        .isEmpty())
                .andExpect(header()
                        .string(
                        "Authorization",
                        startsWith("Bearer ")
                ))
                .andExpect(cookie()
                        .exists("refreshToken"))
                .andReturn();


        // then
        String authorization = result
                        .getResponse()
                        .getHeader("Authorization");

        Cookie refreshTokenCookie = result
                .getResponse()
                .getCookie("refreshToken");

        assertThat(authorization)
                .isNotBlank()
                .startsWith("Bearer ");

        assertThat(refreshTokenCookie)
                .isNotNull();

        assertThat(refreshTokenCookie
                .getValue())
                .isNotBlank();
    }



    @Test
    @DisplayName("회원가입 후 로그인하고 내 정보 조회 성공")
    void getUserSuccess() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username,NICKNAME, PASSWORD);

        LoginResult loginResult = login(username, PASSWORD);

        // when & then
        mockMvc
                .perform(get(USER_ME_URL)
                        .header(
                                "Authorization",
                                loginResult
                                        .accessToken()
                        ))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andExpect(jsonPath("$.data.userId")
                        .isNumber())
                .andExpect(jsonPath("$.data.username")
                        .value(username))
                .andExpect(jsonPath("$.data.role")
                        .exists())
                .andExpect(jsonPath("$.error")
                        .isEmpty());

    }

    @Test
    @DisplayName("회원가입 후 로그인한 회원 삭제 성공")
    void deleteUserSuccess() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username,NICKNAME, PASSWORD);

        LoginResult loginResult = login(username, PASSWORD);

        // when & then
        mockMvc
                .perform(delete(DELETE_USER_URL)
                        .header(
                                "Authorization",
                                loginResult
                                        .accessToken()
                        ))
                .andExpect(status()
                        .isOk())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andExpect(jsonPath("$.data.username")
                        .value(username))
                .andExpect(jsonPath("$.data.deletedAt")
                        .exists())
                .andExpect(jsonPath("$.error")
                        .isEmpty());

    }


    private String createUniqueUsername() {
        return "test-" + UUID.randomUUID()
                .toString()
                .substring(0, 8)
                + "@example.com";
    }

    private void createUser(
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
                        true);

        mockMvc.perform(post(CREATE_USER_URL)
                        .contentType(MediaType
                                .APPLICATION_JSON)
                        .content(objectMapper
                                .writeValueAsString(request)))
                .andExpect(status()
                        .isCreated())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andExpect(jsonPath("$.data.username")
                        .value(username));
    }

    @Test
    @DisplayName("잘못된 비밀번호로 로그인하면 인증 실패")
    void loginFailWithInvalidPassword() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username,NICKNAME, PASSWORD);

        LoginRequest request = new LoginRequest(
                username,
                "wrong-password");

        // when & then
        mockMvc
                .perform(post(LOGIN_URL)
                        .contentType(MediaType
                                .APPLICATION_JSON)
                        .content(objectMapper
                                .writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success")
                        .value(false))
                .andExpect(jsonPath("$.data")
                        .isEmpty())
                .andExpect(jsonPath("$.error.errorCode")
                        .value("AUTH_FAILED"))
                .andExpect(jsonPath("$.error.message")
                        .value("아이디 또는 비밀번호가 일치하지 않습니다."));

    }

    @Test
    @DisplayName("존재하지 않는 회원으로 로그인하면 인증 실패")
    void loginFailWithNotFoundUser() throws Exception {

        // given
        String username = createUniqueUsername();

        LoginRequest request = new LoginRequest(username, PASSWORD);

        // when & then
        mockMvc
                .perform(post(LOGIN_URL)
                        .contentType(MediaType
                                .APPLICATION_JSON)
                        .content(objectMapper
                                .writeValueAsString(request)))
                .andExpect(status()
                        .isUnauthorized())
                .andExpect(jsonPath("$.success")
                        .value(false))
                .andExpect(jsonPath("$.data")
                        .isEmpty())
                .andExpect(jsonPath("$.error.errorCode")
                        .value("AUTH_FAILED"))
                .andExpect(jsonPath("$.error.message")
                        .value("아이디 또는 비밀번호가 일치하지 않습니다."));
    }

    @Test
    @DisplayName("Access Token 없이 내 정보 조회 시 인증 실패")
    void getUserUnauthorized() throws Exception {

        mockMvc
                .perform(get(USER_ME_URL))
                .andExpect(status()
                        .isForbidden());
    }

    @Test
    @DisplayName("Access Token 없이 회원 삭제 시 인증 실패")
    void deleteUserUnauthorized() throws Exception {

        mockMvc
                .perform(delete(DELETE_USER_URL))
                .andExpect(status()
                        .isForbidden());
    }

    private LoginResult login(
            String username,
            String password
    ) throws Exception {

        LoginRequest request =

                new LoginRequest(username, password);

        MvcResult result = mockMvc
                .perform(post(LOGIN_URL)
                        .contentType(MediaType
                                .APPLICATION_JSON)
                        .content(objectMapper
                                .writeValueAsString(request)))
                .andExpect(status()
                        .isOk())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andExpect(jsonPath("$.data.username")
                        .value(username))
                .andExpect(jsonPath("$.data.message")
                        .value("로그인에 성공하였습니다."))
                .andExpect(jsonPath("$.data.loginAt")
                        .exists())
                .andExpect(jsonPath("$.error")
                        .isEmpty())
                .andExpect(header()
                        .string(
                        "Authorization",
                        startsWith("Bearer ")
                ))
                .andExpect(cookie()
                        .exists("refreshToken"))
                .andReturn();


        String accessToken = result
                .getResponse()
                .getHeader("Authorization");

        Cookie refreshTokenCookie = result
                .getResponse()
                .getCookie("refreshToken");

        assertThat(accessToken)
                .isNotBlank()
                .startsWith("Bearer ");

        assertThat(refreshTokenCookie)
                .isNotNull();

        assertThat(refreshTokenCookie
                .getValue())
                .isNotBlank();

        return new LoginResult(
                accessToken,
                refreshTokenCookie
        );

    }

    @Test
    @DisplayName("존재하는 username(email) 중복 조회 시, true 반환")
    void checkUsernameDuplicationExists() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username, NICKNAME, PASSWORD);

        String requestBody = """
            {
              "username": "%s"
            }
            """.formatted(username);

        // when & then
        mockMvc.perform(get(CHECK_USERNAME_DUPLICATION_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.duplicate").value(true))
                .andExpect(jsonPath("$.data.message")
                        .value("이미 존재하는 이메일 입니다."))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("존재하지 않는 username(email) 중복 조회 시, false 반환")
    void checkUsernameDuplicationNotExists() throws Exception {

        // given
        String username = createUniqueUsername();

        String requestBody = """
            {
              "username": "%s"
            }
            """.formatted(username);

        // when & then
        mockMvc.perform(get(CHECK_USERNAME_DUPLICATION_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.duplicate").value(false))
                .andExpect(jsonPath("$.data.message")
                        .value("사용 가능한 이메일 입니다."))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("존재하는 nickname 중복 조회 시, true 반환")
    void checkNicknameDuplicationExists() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username, NICKNAME, PASSWORD);

        String requestBody = """
            {
              "nickname": "%s"
            }
            """.formatted(NICKNAME);

        // when & then
        mockMvc.perform(get(CHECK_NICKNAME_DUPLICATION_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.duplicate").value(true))
                .andExpect(jsonPath("$.data.message")
                        .value("이미 존재하는 닉네임 입니다."))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("존재하지 않는 nickname 중복 조회 시, false 반환")
    void checkNicknameDuplicationNotExists() throws Exception {

        // given
        String nickname = "available-" + UUID.randomUUID()
                .toString()
                .substring(0, 8);

        String requestBody = """
            {
              "nickname": "%s"
            }
            """.formatted(nickname);

        // when & then
        mockMvc.perform(get(CHECK_NICKNAME_DUPLICATION_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.duplicate").value(false))
                .andExpect(jsonPath("$.data.message")
                        .value("사용 가능한 닉네임 입니다."))
                .andExpect(jsonPath("$.error").isEmpty());
    }





}
