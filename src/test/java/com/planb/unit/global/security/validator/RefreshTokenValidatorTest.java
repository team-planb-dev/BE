package com.planb.unit.global.security.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.planb.global.security.util.JwtUtil;
import com.planb.global.security.validator.RefreshTokenValidator;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenValidatorTest {

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private RefreshTokenValidator refreshTokenValidator;

    @Test
    @DisplayName("refresh token이 blank이면 true 반환")
    void isInvalid_true_blank() {

        // given
        String refresh = "";

        // when
        boolean result =
                refreshTokenValidator.isInvalid(refresh);

        // then
        assertThat(result)
                .isTrue();
    }

    @Test

    @DisplayName("refresh token이 만료되면 true 반환")

    void isInvalid_true_expired() {

        // given

        String refresh = "refreshToken";

        when(jwtUtil
                .isExpired(refresh))
                .thenReturn(true);

        // when
        boolean result =
                refreshTokenValidator.isInvalid(refresh);

        // then
        assertThat(result)
                .isTrue();
    }

    @Test
    @DisplayName("refresh category가 아니면 true 반환")
    void isInvalid_true_notRefreshCategory() {

        // given
        String refresh = "refreshToken";

        when(jwtUtil
                .isExpired(refresh))
                .thenReturn(false);

        when(jwtUtil
                .getCategory(refresh))
                .thenReturn("access");

        // when
        boolean result =
                refreshTokenValidator.isInvalid(refresh);

        // then
        assertThat(result)
                .isTrue();

    }

    @Test
    @DisplayName("유효한 refresh token이면 false 반환")
    void isInvalid_false_validRefresh() {

        // given
        String refresh = "refreshToken";

        when(jwtUtil
                .isExpired(refresh))
                .thenReturn(false);

        when(jwtUtil
                .getCategory(refresh))
                .thenReturn("refresh");

        // when
        boolean result =
                refreshTokenValidator.isInvalid(refresh);

        // then
        assertThat(result)
                .isFalse();
    }



}