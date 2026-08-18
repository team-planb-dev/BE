package com.planb.unit.global.security.service;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.dto.response.ReissueResponse;
import com.planb.global.security.repository.UserTokenCacheRepository;
import com.planb.global.security.service.RefreshService;
import com.planb.global.security.util.JwtUtil;
import com.planb.global.utils.web.CookieUtil;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefreshServiceTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserTokenCacheRepository userTokenCacheRepository;

    @Mock
    private CookieUtil cookieUtil;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private RefreshService refreshService;


    @Test
    @DisplayName("refresh cookie가 없을 시, REFRESH_NULL 상태반환")
    void refreshCookies_refreshNull() {

        // given
        when(cookieUtil
                .findCookie(request))
                .thenReturn(null);

        // when
        ReissueResponse response = refreshService
                .refreshCookies(request);

        // then
        assertThat(response.status())
                .isEqualTo(ReissueResponse
                        .ReissueStatus
                        .REFRESH_NULL);

        assertThat(response
                .accessToken())
                .isNull();

        assertThat(response
                .refreshToken())
                .isNull();

        verify(jwtUtil,
                never())
                .isExpired(anyString());

    }

    @Test
    @DisplayName("refresh token이 만료 시, REFRESH_EXPIRED 상태반환")
    void refreshCookies_refreshExpired() {

        // given
        String refresh = "expired-refresh-token";

        when(cookieUtil
                .findCookie(request))
                .thenReturn(refresh);

        doThrow(new ExpiredJwtException(null, null, "expired"))
                .when(jwtUtil)
                .isExpired(refresh);

        // when
        ReissueResponse response = refreshService
                .refreshCookies(request);

        // then
        assertThat(response.status())
                .isEqualTo(ReissueResponse
                        .ReissueStatus
                        .REFRESH_EXPIRED);

        assertThat(response
                .accessToken())
                .isNull();

        assertThat(response
                .refreshToken())
                .isNull();

    }

    // DisplayName 수정 필요
    @Test
    @DisplayName("정상 refresh token이면 access token과 refresh token을 재발급한다")
    void refreshCookies_success() {

        // given

        String oldRefresh = "old-refresh-token";

        String newAccess = "new-access-token";
        String newRefresh = "new-refresh-token";

        String username = "wooju@example.com";
        String role = "ROLE_USER";

        when(cookieUtil
                .findCookie(request))
                .thenReturn(oldRefresh);

        when(jwtUtil
                .isExpired(oldRefresh))
                .thenReturn(false);

        when(jwtUtil
                .getUsername(oldRefresh))
                .thenReturn(username);

        when(jwtUtil
                .getRole(oldRefresh))
                .thenReturn(role);

        when(jwtUtil
                .createJwt("access",
                        username,
                        role,
                        600000 * 6 * 24L))
                .thenReturn(newAccess);

        when(jwtUtil
                .createJwt("refresh",
                        username,
                        role,
                        7 * 600000 * 6 * 24L))
                .thenReturn(newRefresh);

        // when
        ReissueResponse response = refreshService
                .refreshCookies(request);

        // then
        assertThat(response
                .status())
                .isEqualTo(ReissueResponse
                        .ReissueStatus
                        .REFRESH_REISSUED);

        assertThat(response
                .accessToken())
                .isEqualTo(newAccess);

        assertThat(response
                .refreshToken())
                .isEqualTo(newRefresh);

        verify(userTokenCacheRepository)
                .delete("refresh:refreshToken:" + oldRefresh);

        verify(userTokenCacheRepository)
                .delete("refresh:user:" + username);

        verify(userTokenCacheRepository)
                .save("refresh:user:" + username, newRefresh, 7 * 600000 * 6 * 24L);

        verify(userTokenCacheRepository)
                .save("refresh:refreshToken:" + newRefresh, username, 7 * 600000 * 6 * 24L);

    }

    @Test
    @DisplayName("이미 로그인된 username이면 USER_ALREADY_LOGIN 예외가 발생한다")
    void validateAlreadyLogin_alreadyLogin() {

        // given
        String username = "wooju@example.com";

        when(userTokenCacheRepository.exists("refresh:user:" + username))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(() -> refreshService
                .validateAlreadyLogin(username))
                .isInstanceOf(BaseException.class)
                .hasMessage(BaseExceptionEnum
                        .USER_ALREADY_LOGIN
                        .getMessage());

    }

    @Test
    @DisplayName("username이 null이면 refresh token 삭제를 수행하지 않는다")
    void deleteRefresh_usernameNull() {

        // given
        String refresh = "refresh-token";

        when(jwtUtil
                .getUsername(refresh))
                .thenReturn(null);

        // when
        refreshService.deleteRefresh(refresh);

        // then
        verify(userTokenCacheRepository,
                never())
                .delete(anyString());

    }
}