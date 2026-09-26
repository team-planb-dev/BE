package com.planb.unit.global.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import com.planb.global.security.filter.JwtLogoutFilter;
import com.planb.global.security.service.RefreshService;
import com.planb.global.security.service.UserAuthCacheService;
import com.planb.global.security.util.JwtUtil;
import com.planb.global.security.validator.RefreshTokenValidator;
import com.planb.global.utils.web.CookieUtil;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtLogoutFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private RefreshService refreshService;

    @Mock
    private UserAuthCacheService userAuthCacheService;

    @Mock
    private CookieUtil cookieUtil;

    @Mock
    private RefreshTokenValidator refreshTokenValidator;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private TestJwtLogoutFilter jwtLogoutFilter;

    @Test
    @DisplayName("logout 외 요청의 필터 미수행")
    void shouldNotFilter_success_notLogoutRequest()
            throws Exception {

        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setRequestURI("/login");
        request.setMethod("POST");

        // when
        boolean result =
                jwtLogoutFilter.callShouldNotFilter(request);

        // then
        assertThat(result)
                .isTrue();

    }

    @Test
    @DisplayName("logout POST 요청의 필터 수행")
    void shouldNotFilter_fail_logoutRequest()
            throws Exception {

        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request.setRequestURI("/logout");
        request.setMethod("POST");

        // when
        boolean result =
                jwtLogoutFilter.callShouldNotFilter(request);

        // then
        assertThat(result)
                .isFalse();
    }

    @Test
    @DisplayName("refresh 쿠키가 없어도 access 토큰으로 서버 세션 정리")
    void doFilterInternal_fallsBackToAccessToken()
            throws Exception {

        // given
        String accessToken = "accessToken";
        String username = "testUser@example.com";

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request
                .addHeader("Authorization", "Bearer " + accessToken);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        Cookie zeroCookie =
                new Cookie("refreshToken", null);

        when(cookieUtil
                .findCookie(request))
                .thenReturn(null);

        when(refreshTokenValidator
                .isInvalid(null))
                .thenReturn(true);

        when(jwtUtil
                .getUsernameAllowingExpired(accessToken))
                .thenReturn(username);

        when(cookieUtil
                .zeroCookie(response))
                .thenReturn(zeroCookie);

        // when
        jwtLogoutFilter.callDoFilterInternal(
                request,
                response,
                filterChain
        );

        // then
        verify(refreshService, times(1))
                .deleteRefreshByUsername(username);

        verify(userAuthCacheService, times(1))
                .deleteUserAuthCache(username);

        verify(filterChain, never())
                .doFilter(request, response);

        assertThat(response.getStatus())
                .isEqualTo(200);
    }

    @Test
    @DisplayName("refresh 쿠키도 access 토큰도 없으면 401 응답")
    void doFilterInternal_noCredential()
            throws Exception {

        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        when(cookieUtil
                .findCookie(request))
                .thenReturn(null);

        when(refreshTokenValidator
                .isInvalid(null))
                .thenReturn(true);

        // when
        jwtLogoutFilter.callDoFilterInternal(
                request,
                response,
                filterChain
        );

        // then
        assertThat(response.getStatus())
                .isEqualTo(401);

        verify(filterChain, never())
                .doFilter(request, response);

        verify(refreshService, never())
                .deleteRefreshByUsername(anyString());
    }

    @Test

    @DisplayName("유효한 refresh 토큰인 경우 로그아웃 수행")

    void doFilterInternal_success()

            throws Exception {

        // given
        String refresh = "refreshToken";
        String username = "testUser@example.com";

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        Cookie zeroCookie =
                new Cookie("refreshToken", null);

        when(cookieUtil
                .findCookie(request))
                .thenReturn(refresh);

        when(jwtUtil
                .getUsername(refresh))
                .thenReturn(username);

        when(refreshTokenValidator
                .isInvalid(refresh))
                .thenReturn(false);

        when(cookieUtil
                .zeroCookie(response))
                .thenReturn(zeroCookie);

        // when
        jwtLogoutFilter
                .callDoFilterInternal(request, response, filterChain);

        // then
        verify(refreshService, times(1))
                .deleteRefresh(refresh);

        verify(userAuthCacheService)
                .deleteUserAuthCache(username);

        verify(cookieUtil, times(1))
                .zeroCookie(response);

        Cookie responseCookie = response
                .getCookie("refreshToken");

        assertThat(responseCookie)
                .isNotNull();

        assertThat(response.getStatus())
                .isEqualTo(200);

        assertThat(response.getContentAsString())
                .contains("로그아웃에 성공하였습니다.");
    }


    private static class TestJwtLogoutFilter

            extends JwtLogoutFilter {

        public TestJwtLogoutFilter(JwtUtil jwtUtil,
                                   RefreshService refreshService,
                                   UserAuthCacheService userAuthCacheService,
                                   CookieUtil cookieUtil,
                                   RefreshTokenValidator refreshTokenValidator) {

            super(jwtUtil,
                    refreshService,
                    userAuthCacheService,
                    cookieUtil,
                    refreshTokenValidator);

        }

        // shouldNotFilter 메소드 호출기
        public boolean callShouldNotFilter
                (MockHttpServletRequest request)
                throws Exception {

            return shouldNotFilter(request);
        }

        // callDoFilterInternal 메소드 호출기
        public void callDoFilterInternal
                (MockHttpServletRequest request,
                 MockHttpServletResponse response,
                 FilterChain filterChain)
                throws Exception {

            doFilterInternal(request, response, filterChain);
        }

    }

}
