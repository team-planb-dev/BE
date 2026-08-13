package com.planb.unit.global.security.provider;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.auth.AuthPrincipal;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.provider.JwtAuthenticationProvider;
import com.planb.global.security.repository.UserAuthCacheRepository;
import com.planb.global.security.util.JwtUtil;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationProviderTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserAuthCacheRepository userAuthCacheRepository;

    @InjectMocks
    private JwtAuthenticationProvider jwtAuthenticationProvider;

    @Test
    @DisplayName("JWT 인증 성공")
    void authenticateSuccess() {
        // given
        String authorizationHeader = "Bearer access-token";
        String parsedToken = "access-token";
        String username = "testUser@example.com";

        UserAuthCache userAuthCache = new UserAuthCache(
                1L,
                username,
                "ROLE_USER"
        );

        when(jwtUtil
                .isExpired(parsedToken))
                .thenReturn(false);

        when(jwtUtil
                .getCategory(parsedToken))
                .thenReturn("access");

        when(jwtUtil
                .getUsername(parsedToken))
                .thenReturn(username);

        when(userAuthCacheRepository
                .findByUsername(username))
                .thenReturn(Optional
                        .of(userAuthCache));

        // when
        Authentication authentication = jwtAuthenticationProvider
                .authenticate(authorizationHeader);

        // then
        assertThat(authentication)
                .isNotNull();

        assertThat(authentication
                .isAuthenticated())
                .isTrue();

        assertThat(authentication
                .getPrincipal())
                .isInstanceOf(AuthPrincipal.class);

        assertThat(authentication
                .getCredentials())
                .isNull();

        assertThat(authentication
                .getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_USER");

        AuthPrincipal authPrincipal = (AuthPrincipal) authentication
                .getPrincipal();

        assertThat(authPrincipal
                .getUsername())
                .isEqualTo(username);

        verify(jwtUtil).isExpired(parsedToken);

        verify(jwtUtil).getCategory(parsedToken);

        verify(jwtUtil).getUsername(parsedToken);

        verify(userAuthCacheRepository).findByUsername(username);
    }

    @Test
    @DisplayName("Authorization 헤더가 null일 경우, 인증 실패")
    void authenticateFailWhenAuthorizationHeaderIsNull() {

        // when & then
        assertThatThrownBy(
                () -> jwtAuthenticationProvider.authenticate(null)
        )
                .isInstanceOf(BaseException.class);

        verify(jwtUtil,never())
                .isExpired(anyString());

        verify(userAuthCacheRepository,never())
                .findByUsername(anyString());
    }

    @Test
    @DisplayName("Authorization 헤더가 공백이면 인증 실패")
    void authenticateFailWhenAuthorizationHeaderIsBlank() {
        // when & then
        assertThatThrownBy(
                () -> jwtAuthenticationProvider.authenticate(" ")
        )
                .isInstanceOf(BaseException.class);

        verify(jwtUtil,never())
                .isExpired(anyString());

        verify(userAuthCacheRepository, never())
                .findByUsername(anyString());
    }

    @Test
    @DisplayName("Bearer 접두사가 없을 경우, 인증 실패")
    void authenticateFailWhenBearerPrefixIsMissing() {

        // given
        String authorizationHeader = "access-token";

        // when & then
        assertThatThrownBy(
                () -> jwtAuthenticationProvider.authenticate(authorizationHeader)
        )
                .isInstanceOf(BaseException.class);

        verify(jwtUtil, never())
                .isExpired(anyString());

        verify(userAuthCacheRepository, never())
                .findByUsername(anyString());
    }

    @Test
    @DisplayName("토큰이 만료될 경우, 인증 실패")
    void authenticateFailWhenTokenIsExpired() {

        // given
        String authorizationHeader = "Bearer access-token";
        String parsedToken = "access-token";

        when(jwtUtil
                .isExpired(parsedToken))
                .thenReturn(true);

        // when & then
        assertThatThrownBy(
                () -> jwtAuthenticationProvider.authenticate(authorizationHeader)
        )
                .isInstanceOf(BaseException.class);

        verify(jwtUtil)
                .isExpired(parsedToken);

        verify(jwtUtil, never())
                .getCategory(parsedToken);

        verify(jwtUtil, never())
                .getUsername(parsedToken);

        verify(userAuthCacheRepository, never())
                .findByUsername(anyString());
    }

    @Test
    @DisplayName("Access 토큰이 아닐 경우, 인증 실패")
    void authenticateFailWhenTokenCategoryIsNotAccess() {

        // given
        String authorizationHeader = "Bearer refresh-token";
        String parsedToken = "refresh-token";

        when(jwtUtil
                .isExpired(parsedToken))
                .thenReturn(false);

        when(jwtUtil
                .getCategory(parsedToken))
                .thenReturn("refresh");

        // when & then
        assertThatThrownBy(
                () -> jwtAuthenticationProvider.authenticate(authorizationHeader)
        )
                .isInstanceOf(BaseException.class);

        verify(jwtUtil).isExpired(parsedToken);

        verify(jwtUtil).getCategory(parsedToken);

        verify(jwtUtil, never())
                .getUsername(parsedToken);

        verify(userAuthCacheRepository, never())
                .findByUsername(anyString());
    }

    @Test
    @DisplayName("Redis 캐시에 사용자가 없을 경우, 인증 실패")
    void authenticateFailWhenUserDoesNotExistInCache() {

        // given
        String authorizationHeader = "Bearer access-token";
        String parsedToken = "access-token";
        String username = "unknownUser@example.com";

        when(jwtUtil
                .isExpired(parsedToken))
                .thenReturn(false);

        when(jwtUtil
                .getCategory(parsedToken))
                .thenReturn("access");

        when(jwtUtil
                .getUsername(parsedToken))
                .thenReturn(username);

        when(userAuthCacheRepository
                .findByUsername(username))
                .thenReturn(Optional
                        .empty());

        // when & then
        assertThatThrownBy(
                () -> jwtAuthenticationProvider.authenticate(authorizationHeader)
        )
                .isInstanceOf(BaseException.class);

        verify(jwtUtil).isExpired(parsedToken);

        verify(jwtUtil).getCategory(parsedToken);

        verify(jwtUtil).getUsername(parsedToken);

        verify(userAuthCacheRepository).findByUsername(username);
    }
}