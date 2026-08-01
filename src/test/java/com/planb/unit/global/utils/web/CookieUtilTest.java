package com.planb.unit.global.utils.web;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import com.planb.global.utils.web.CookieUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)

class CookieUtilTest {

    private final CookieUtil cookieUtil =
            new CookieUtil();

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Test
    @DisplayName("쿠키 생성 성공")
    void createCookie_success() {

        // given
        String key = "refreshToken";
        String value = "testRefreshToken";

        // when
        Cookie cookie = cookieUtil
                .createCookie(key,value);

        // then
        assertThat(cookie
                .getName())
                .isEqualTo(key);

        assertThat(cookie
                .getValue())
                .isEqualTo(value);

        assertThat(cookie
                .isHttpOnly())
                .isTrue();

        assertThat(cookie
                .getMaxAge())
                .isEqualTo(24 * 60 * 60);
    }

    @Test
    @DisplayName("쿠키 제거 성공")
    void zeroCookie_success() {

        // when
        Cookie cookie = cookieUtil.zeroCookie(response);

        // then
        assertThat(cookie
                .getName())
                .isEqualTo("refreshToken");

        assertThat(cookie
                .getValue())
                .isNull();

        assertThat(cookie
                .getMaxAge())
                .isEqualTo(0);

        assertThat(cookie
                .getPath())
                .isEqualTo("/");
    }

    @Test
    @DisplayName("refreshToken 쿠키 조회 성공")
    void findCookie_success() {

        // given
        Cookie refreshCookie =
                new Cookie("refreshToken", "refresh");

        Cookie[] cookies = {refreshCookie};

        when(request.getCookies())
                .thenReturn(cookies);

        // when
        String refresh = cookieUtil
                .findCookie(request);

        // then
        assertThat(refresh)
                .isEqualTo("refresh");
    }

    @Test
    @DisplayName("refreshToken 쿠키가 없으면 null 반환")
    void findCookie_fail_cookieNotFound() {

        // given
        Cookie anotherCookie =
                new Cookie("anotherCookie", "anotherValue");

        Cookie[] cookies = {anotherCookie};

        when(request
                .getCookies())
                .thenReturn(cookies);

        // when
        String refresh = cookieUtil
                .findCookie(request);

        // then
        assertThat(refresh)
                .isNull();
    }

}