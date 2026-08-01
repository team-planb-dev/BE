package com.planb.unit.global.security.util;

import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.junit.jupiter.MockitoExtension;
import com.planb.global.security.util.JwtUtil;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class JwtUtilTest {

    private final String SECRET_KEY =
            "Testing is always a hassle. But I do it because I have to.";

    private final JwtUtil jwtUtil =
            new JwtUtil(SECRET_KEY);

    @Test
    @DisplayName("JWT 생성 성공")
    void createJWT_Success(){


        // given
        String category = "access";
        String username = "wooju";
        String role = "ROLE_USER";
        Long expiredMs = 1000L * 60;


        // when
        String token = jwtUtil
                .createJwt(category,
                        username,
                        role,
                        expiredMs);

        // then
        assertThat(token).isNotNull();
        assertThat(token).isNotBlank();

    }

    @Test
    @DisplayName("JWT에서 username 추출 성공")
    void getUsername_Success() {

        // given
        String token = jwtUtil.createJwt(
                "access",
                "wooju",
                "ROLE_USER",
                1000L * 60
        );

        // when
        String username = jwtUtil
                .getUsername(token);

        // then

        assertThat(username)
                .isEqualTo("wooju");

    }

    @Test
    @DisplayName("JWT에서 role 추출 성공")
    void getRole_success() {

        // given
        String token = jwtUtil.createJwt(
                "access",
                "wooju",
                "ROLE_USER",
                1000L * 60
        );

        // when
        String role = jwtUtil
                .getRole(token);

        // then
        assertThat(role)
                .isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("JWT에서 category 추출 성공")
    void getCategory_Success() {

        // given
        String token = jwtUtil.createJwt(
                "refresh",
                "wooju",
                "ROLE_USER",
                1000L * 60
        );

        // when
        String category = jwtUtil
                .getCategory(token);

        // then
        assertThat(category)
                .isEqualTo("refresh");

    }

    @Test
    @DisplayName("JWT 만료 여부 확인 - 만료되지 않은 토큰")
    void isExpired_false() {

        // given
        String token = jwtUtil.createJwt(
                "access",
                "wooju",
                "ROLE_USER",
                1000L * 60
        );

        // when
        Boolean expired = jwtUtil
                .isExpired(token);

        // then
        assertThat(expired)
                .isFalse();
    }

    @Test
    @DisplayName("JWT 만료 여부 확인 - 만료된 토큰")
    void isExpired_true()
            throws
            InterruptedException {

        // given
        String token = jwtUtil.createJwt(
                "access",
                "wooju",
                "ROLE_USER",
                1L

        );

        Thread.sleep(10);

        // when & then
        assertThatThrownBy(() -> jwtUtil
                .isExpired(token))
                .isInstanceOf(JwtException.class);
    }

}