package com.planb.integration.domain.user;


import com.planb.domain.user.entity.constant.RecoveryQuestion;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

import com.planb.integration.IntegrationTest;
import com.planb.global.security.repository.UserAuthCacheRepository;
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

    private static final String REISSUE_URL =
            "/api/v1/refresh/reissue";

    private static final String LOGOUT_URL =
            "/logout";

    private static final String FIND_USERNAME_URL =
            "/api/v1/user/recovery/username";

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

    @Autowired
    private UserAuthCacheRepository userAuthCacheRepository;


    @Test
    @DisplayName("회원가입 성공")
    void createUserSuccess() throws Exception {

        // given
        String username = createUniqueUsername();

        UserCreateRequest request =
                new UserCreateRequest(username,
                        createUniqueNickname(),
                        PASSWORD,
                        RecoveryQuestion.FIRST_PET,
                        "콩이",
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
                createUniqueNickname(),
                PASSWORD);

        LoginRequest request =
                new LoginRequest(username, PASSWORD);

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
    @DisplayName("Refresh Token Cookie로 인증 없이 토큰 재발급 성공")
    void reissueWithoutAccessTokenSuccess() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(
                username,
                createUniqueNickname(),
                PASSWORD
        );

        LoginResult loginResult = login(
                username,
                PASSWORD
        );

        // when & then
        mockMvc.perform(
                        post(REISSUE_URL)
                                .cookie(
                                        loginResult.refreshTokenCookie()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.status")
                                .value("REFRESH_REISSUED")
                )
                .andExpect(
                        jsonPath("$.data.accessToken")
                                .isNotEmpty()
                )
                .andExpect(
                        jsonPath("$.data.refreshToken")
                                .isNotEmpty()
                );
    }

    @Test
    @DisplayName("재발급 응답의 새 Refresh Token 쿠키로 연속 재발급 성공")
    void reissueTwiceWithRotatedCookieSuccess() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(
                username,
                createUniqueNickname(),
                PASSWORD
        );

        LoginResult loginResult = login(
                username,
                PASSWORD
        );

        // when - 첫 재발급은 서버에 저장된 옛 Refresh Token을 지운다
        MvcResult firstResult = mockMvc.perform(
                        post(REISSUE_URL)
                                .cookie(
                                        loginResult.refreshTokenCookie()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.data.status")
                                .value("REFRESH_REISSUED")
                )
                .andReturn();

        Cookie rotatedCookie = firstResult
                .getResponse()
                .getCookie("refreshToken");

        // then - 쿠키를 갱신해 주지 않으면 브라우저는 지워진 토큰을 계속 보낸다
        assertThat(rotatedCookie)
                .isNotNull();

        // 토큰 값 자체는 비교하지 않는다. iat가 초 단위라 같은 초에 재발급하면
        // 문자열이 옛것과 같아질 수 있다. 확인할 것은 쿠키가 내려오는지와
        // 그 쿠키로 다음 재발급이 되는지다.
        mockMvc.perform(
                        post(REISSUE_URL)
                                .cookie(rotatedCookie)
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.status")
                                .value("REFRESH_REISSUED")
                );
    }

    @Test
    @DisplayName("로그아웃 후 기존 Access Token 사용 거부")
    void accessTokenRejectedAfterLogout() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(
                username,
                createUniqueNickname(),
                PASSWORD
        );

        LoginResult loginResult = login(
                username,
                PASSWORD
        );

        // when
        mockMvc.perform(
                        post(LOGOUT_URL)
                                .cookie(
                                        loginResult.refreshTokenCookie()
                                )
                )
                .andExpect(status().isOk());

        // then
        mockMvc.perform(
                        get(USER_ME_URL)
                                .header(
                                        "Authorization",
                                        loginResult.accessToken()
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                );
    }



    @Test
    @DisplayName("회원가입 후 로그인하고 내 정보 조회 성공")
    void getUserSuccess() throws Exception {

        // given
        String username = createUniqueUsername();
        String nickname = createUniqueNickname();

        createUser(username, nickname, PASSWORD);

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
                .andExpect(jsonPath("$.data.nickname")
                        .value(nickname))
                .andExpect(jsonPath("$.data.role")
                        .exists())
                .andExpect(jsonPath("$.error")
                        .isEmpty());

        // 세션 식별자는 인증 내부 값이라 응답에 나가지 않아야 한다.
        mockMvc
                .perform(get(USER_ME_URL)
                        .header(
                                "Authorization",
                                loginResult
                                        .accessToken()
                        ))
                .andExpect(jsonPath("$.data.sessionId")
                        .doesNotExist());

    }

    @Test
    @DisplayName("회원가입 후 로그인한 회원 삭제 성공")
    void deleteUserSuccess() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username, createUniqueNickname(), PASSWORD);

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

    @Test
    @DisplayName("회원탈퇴 후 기존 Access Token과 Refresh Token 사용 거부")
    void deletedUserTokensRejected() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(
                username,
                createUniqueNickname(),
                PASSWORD
        );

        LoginResult loginResult = login(
                username,
                PASSWORD
        );

        mockMvc.perform(
                        delete(DELETE_USER_URL)
                                .header(
                                        "Authorization",
                                        loginResult.accessToken()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        // when & then
        mockMvc.perform(
                        get(USER_ME_URL)
                                .header(
                                        "Authorization",
                                        loginResult.accessToken()
                                )
                )
                .andExpect(status().isUnauthorized())
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                );

        mockMvc.perform(
                        post(REISSUE_URL)
                                .cookie(
                                        loginResult.refreshTokenCookie()
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(false)
                )
                .andExpect(
                        jsonPath("$.error.errorCode")
                                .value(
                                        "BASE.EXCEPTION.REFRESH_TOKEN_NOT_FOUND"
                                )
                );
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

        createUser(username, nickname, password, "콩이");
    }

    // 닉네임은 계정마다 유일해야 하므로 픽스처도 매번 다른 값을 쓴다.
    private String createUniqueNickname() {

        return NICKNAME + "-" + UUID
                .randomUUID()
                .toString()
                .substring(0, 8);
    }

    private void createUser(
            String username,
            String nickname,
            String password,
            String recoveryAnswer
    ) throws Exception {

        UserCreateRequest request =
                new UserCreateRequest(
                        username,
                        nickname,
                        password,
                        RecoveryQuestion.FIRST_PET,
                        recoveryAnswer,
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

        createUser(username, createUniqueNickname(), PASSWORD);

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

        createUser(username, createUniqueNickname(), PASSWORD);

        // when & then
        mockMvc.perform(get(CHECK_USERNAME_DUPLICATION_URL)
                        .param("username", username))
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

        // when & then
        mockMvc.perform(get(CHECK_USERNAME_DUPLICATION_URL)
                        .param("username", username))
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

        String nickname = createUniqueNickname();

        createUser(username, nickname, PASSWORD);

        // when & then
        mockMvc.perform(get(CHECK_NICKNAME_DUPLICATION_URL)
                        .param("nickname", nickname))
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

        // when & then
        mockMvc.perform(get(CHECK_NICKNAME_DUPLICATION_URL)
                        .param("nickname", nickname))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.duplicate").value(false))
                .andExpect(jsonPath("$.data.message")
                        .value("사용 가능한 닉네임 입니다."))
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("재로그인 시 이전 로그인의 access 토큰 거부")
    void reLoginRevokesPreviousAccessToken() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username, createUniqueNickname(), PASSWORD);

        LoginResult first = login(username, PASSWORD);

        // when
        LoginResult second = login(username, PASSWORD);

        // then
        mockMvc.perform(
                        get(USER_ME_URL)
                                .header("Authorization", first.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));

        mockMvc.perform(
                        get(USER_ME_URL)
                                .header("Authorization", second.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("로그아웃 후 같은 계정으로 재로그인 성공")
    void reLoginAfterLogoutSucceeds() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username, createUniqueNickname(), PASSWORD);

        LoginResult first = login(username, PASSWORD);

        mockMvc.perform(
                        post(LOGOUT_URL)
                                .cookie(first.refreshTokenCookie()))
                .andExpect(status().isOk());

        // when
        LoginResult second = login(username, PASSWORD);

        // then
        mockMvc.perform(
                        get(USER_ME_URL)
                                .header("Authorization", second.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("refresh 쿠키 없이 로그아웃해도 서버 세션 정리")
    void logoutWithoutRefreshCookieClearsServerSession() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username, createUniqueNickname(), PASSWORD);

        LoginResult loginResult = login(username, PASSWORD);

        // when
        mockMvc.perform(
                        post(LOGOUT_URL)
                                .header("Authorization", loginResult.accessToken()))
                .andExpect(status().isOk());

        // then
        mockMvc.perform(
                        get(USER_ME_URL)
                                .header("Authorization", loginResult.accessToken()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @DisplayName("재발급한 access 토큰으로 내 정보 조회 성공")
    void reissuedAccessTokenAuthenticates() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username, createUniqueNickname(), PASSWORD);

        LoginResult loginResult = login(username, PASSWORD);

        // when
        MvcResult reissued = mockMvc
                .perform(post(REISSUE_URL)
                        .cookie(loginResult.refreshTokenCookie()))
                .andExpect(status().isOk())
                .andReturn();

        String reissuedAccessToken = objectMapper
                .readTree(reissued
                        .getResponse()
                        .getContentAsString())
                .path("data")
                .path("accessToken")
                .asString();

        // then
        mockMvc.perform(
                        get(USER_ME_URL)
                                .header("Authorization", "Bearer " + reissuedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("다른 계정의 로그인은 기존 계정 세션에 영향 없음")
    void loginOfOtherAccountKeepsExistingSession() throws Exception {

        // given
        String username = createUniqueUsername();
        String otherUsername = createUniqueUsername();

        createUser(username, createUniqueNickname(), PASSWORD);
        createUser(otherUsername, createUniqueNickname(), PASSWORD);

        LoginResult loginResult = login(username, PASSWORD);

        // when
        login(otherUsername, PASSWORD);

        // then
        mockMvc.perform(
                        get(USER_ME_URL)
                                .header("Authorization", loginResult.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("인증 캐시가 만료된 뒤 재발급한 access 토큰으로 조회 성공")
    void reissueRestoresExpiredAuthCache() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username, createUniqueNickname(), PASSWORD);

        LoginResult loginResult = login(username, PASSWORD);

        // 인증 캐시는 access 토큰과 같은 수명을 갖는다.
        // 그 뒤 refresh로 재발급하는 상황을 만들려면 캐시만 먼저 지워야 한다.
        userAuthCacheRepository.delete(username);

        mockMvc.perform(
                        get(USER_ME_URL)
                                .header("Authorization", loginResult.accessToken()))
                .andExpect(status().isUnauthorized());

        // when
        MvcResult reissued = mockMvc
                .perform(post(REISSUE_URL)
                        .cookie(loginResult.refreshTokenCookie()))
                .andExpect(status().isOk())
                .andReturn();

        String reissuedAccessToken = objectMapper
                .readTree(reissued
                        .getResponse()
                        .getContentAsString())
                .path("data")
                .path("accessToken")
                .asString();

        // then
        mockMvc.perform(
                        get(USER_ME_URL)
                                .header("Authorization", "Bearer " + reissuedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("닉네임과 한글 복구 답변으로 이메일 찾기 성공")
    void findUsernameWithKoreanAnswer() throws Exception {

        // given
        String username = createUniqueUsername();
        String nickname = createUniqueNickname();

        String koreanAnswer = "나비" + UUID.randomUUID()
                .toString()
                .substring(0, 8);

        createUser(username, nickname, PASSWORD, koreanAnswer);

        String requestBody = """
            {
              "nickname": "%s",
              "recoveryQuestion": "FIRST_PET",
              "recoveryAnswer": "%s"
            }
            """.formatted(nickname, koreanAnswer);

        // when & then
        mockMvc.perform(post(FIND_USERNAME_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maskedUsername")
                        .value(startsWith(username.substring(0, 2) + "***")));
    }

    @Test
    @DisplayName("같은 복구 답변을 쓴 계정이 여럿이어도 닉네임으로 한 건만 특정")
    void findUsernameWhenAnswerSharedByMultipleAccounts() throws Exception {

        // given
        String sharedAnswer = "나비" + UUID.randomUUID()
                .toString()
                .substring(0, 8);

        String username = createUniqueUsername();
        String nickname = createUniqueNickname();

        createUser(username, nickname, PASSWORD, sharedAnswer);
        createUser(createUniqueUsername(), createUniqueNickname(), PASSWORD, sharedAnswer);

        String requestBody = """
            {
              "nickname": "%s",
              "recoveryQuestion": "FIRST_PET",
              "recoveryAnswer": "%s"
            }
            """.formatted(nickname, sharedAnswer);

        // when & then
        mockMvc.perform(post(FIND_USERNAME_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.maskedUsername")
                        .value(startsWith(username.substring(0, 2) + "***")));
    }

    @Test
    @DisplayName("닉네임은 맞지만 복구 답변이 다르면 이메일 찾기 실패")
    void findUsernameWithWrongAnswer() throws Exception {

        // given
        String nickname = createUniqueNickname();

        createUser(createUniqueUsername(), nickname, PASSWORD, "나비");

        String requestBody = """
            {
              "nickname": "%s",
              "recoveryQuestion": "FIRST_PET",
              "recoveryAnswer": "%s"
            }
            """.formatted(nickname, "없는답변" + UUID.randomUUID());

        // when & then
        mockMvc.perform(post(FIND_USERNAME_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.errorCode")
                        .value("BASE.EXCEPTION.RECOVERY_ANSWER_MISMATCH"));
    }

    @Test
    @DisplayName("존재하지 않는 닉네임이면 이메일 찾기 실패")
    void findUsernameWithUnknownNickname() throws Exception {

        // given
        String requestBody = """
            {
              "nickname": "%s",
              "recoveryQuestion": "FIRST_PET",
              "recoveryAnswer": "나비"
            }
            """.formatted(createUniqueNickname());

        // when & then
        mockMvc.perform(post(FIND_USERNAME_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.errorCode")
                        .value("BASE.EXCEPTION.RECOVERY_ANSWER_MISMATCH"));
    }

    @Test
    @DisplayName("이미 사용 중인 닉네임으로 가입 시 실패")
    void createUserRejectsDuplicateNickname() throws Exception {

        // given
        String nickname = createUniqueNickname();

        createUser(createUniqueUsername(), nickname, PASSWORD);

        UserCreateRequest request =
                new UserCreateRequest(createUniqueUsername(),
                        nickname,
                        PASSWORD,
                        RecoveryQuestion.FIRST_PET,
                        "콩이",
                        true,
                        true,
                        true);

        // when & then
        mockMvc.perform(post(CREATE_USER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.errorCode")
                        .value("BASE.EXCEPTION.DUPLICATE_NICKNAME"));
    }

    @Test
    @DisplayName("이미 사용 중인 이메일로 가입 시 실패")
    void createUserRejectsDuplicateUsername() throws Exception {

        // given
        String username = createUniqueUsername();

        createUser(username, createUniqueNickname(), PASSWORD);

        UserCreateRequest request =
                new UserCreateRequest(username,
                        createUniqueNickname(),
                        PASSWORD,
                        RecoveryQuestion.FIRST_PET,
                        "콩이",
                        true,
                        true,
                        true);

        // when & then
        mockMvc.perform(post(CREATE_USER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.errorCode")
                        .value("BASE.EXCEPTION.DUPLICATE_USERNAME"));
    }

}
