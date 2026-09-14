package com.planb.unit.global.security.service;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.dto.response.ReissueResponse;
import com.planb.global.security.repository.UserTokenCacheRepository;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.service.RefreshService;
import com.planb.global.security.service.UserAuthCacheService;
import com.planb.global.security.util.JwtUtil;
import com.planb.global.security.util.TokenExpiration;
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
    private UserAuthCacheService userAuthCacheService;

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
    @DisplayName("정상 refresh token인 경우 access token과 refresh token 재발급")
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

        when(userTokenCacheRepository
                .exists("refresh:refreshToken:" + oldRefresh))
                .thenReturn(true);

        when(jwtUtil
                .getUsername(oldRefresh))
                .thenReturn(username);

        when(jwtUtil
                .getRole(oldRefresh))
                .thenReturn(role);

        when(jwtUtil
                .getUserId(oldRefresh))
                .thenReturn(7L);

        when(jwtUtil
                .getSessionId(oldRefresh))
                .thenReturn("sess-1");

        when(jwtUtil
                .createJwt("access",
                        7L,
                        username,
                        role,
                        "sess-1",
                        TokenExpiration.ACCESS_TOKEN_EXPIRED_MS))
                .thenReturn(newAccess);

        when(jwtUtil
                .createJwt("refresh",
                        7L,
                        username,
                        role,
                        "sess-1",
                        TokenExpiration.REFRESH_TOKEN_EXPIRED_MS))
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
                .save("refresh:user:" + username, newRefresh, TokenExpiration.REFRESH_TOKEN_EXPIRED_MS);

        verify(userTokenCacheRepository)
                .save("refresh:refreshToken:" + newRefresh, username, TokenExpiration.REFRESH_TOKEN_EXPIRED_MS);

        // 인증 캐시는 로그인에서만 저장되어 access 토큰보다 먼저 사라진다.
        // 재발급이 같은 세션으로 다시 채워야 새 access 토큰이 실제로 쓸모가 있다.
        ArgumentCaptor<UserAuthCache> cacheCaptor =
                ArgumentCaptor.forClass(UserAuthCache.class);

        verify(userAuthCacheService)
                .saveUserAuthCache(cacheCaptor.capture());

        UserAuthCache restored = cacheCaptor.getValue();

        assertThat(restored.userId())
                .isEqualTo(7L);

        assertThat(restored.username())
                .isEqualTo(username);

        assertThat(restored.role())
                .isEqualTo(role);

        assertThat(restored.sessionId())
                .isEqualTo("sess-1");

    }

    @Test
    @DisplayName("username이 null인 경우 refresh token 삭제 미수행")
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
