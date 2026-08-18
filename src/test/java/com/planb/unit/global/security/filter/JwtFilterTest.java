package com.planb.unit.global.security.filter;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.filter.JwtFilter;
import com.planb.global.security.repository.UserAuthCacheRepository;
import com.planb.global.security.util.JwtUtil;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtFilterTest {

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserAuthCacheRepository userAuthCacheRepository;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private JwtFilter jwtFilter;

    // 테스트 후 , 컨텍스트 초기화
    @AfterEach
    void tearDown(){
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Authorization 헤드가 없으면 다음 필터로")
    void doFilterInternal_noAuthorizationHeader()
            throws Exception{

        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        // when
        jwtFilter.doFilter(request,response,filterChain);

        // then
        verify(filterChain,
                times(1))
                .doFilter(request,response);

        verifyNoInteractions(jwtUtil);
        verifyNoInteractions(userAuthCacheRepository);
    }

    @Test
    @DisplayName("Authorization 헤더가 Bearer 형식이 아니면 다음 필터로")
    void doFilterInternal_invalidAuthorizationHeader()
            throws Exception{

        // given
        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request
                .addHeader("Authorization","accessToken");

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        // when
        jwtFilter.doFilter(request,response,filterChain);

        // then
        verify(filterChain,
                times(1))
                .doFilter(request,response);

        verifyNoInteractions(jwtUtil);
        verifyNoInteractions(userAuthCacheRepository);
    }

    @Test
    @DisplayName("access 토큰이 만료시 , 401 응답반환")
    void doFilterInternal_expiredAccessToken()
            throws Exception{

        // given
        String accessToken = "expiredAccessToken";

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        request
                .addHeader("Authorization","Bearer "+accessToken);

        when(jwtUtil
                .isExpired(accessToken))
                .thenThrow(mock(ExpiredJwtException.class));

        // when
        jwtFilter
                .doFilter(request,response,filterChain);

        // then
        assertThat(response
                .getStatus())
                .isEqualTo(401);

        verify(filterChain,never())
                .doFilter(request,response);

    }

    @Test
    @DisplayName("access 토큰의 카테고리 불일치 시, 401 응답 반환")
    void doFilterInternal_invalidTokenCategory()
            throws Exception{

        // given
        String accessToken = "accessToken";

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request
                .addHeader("Authorization",
                        "Bearer "+accessToken);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        when(jwtUtil.isExpired(accessToken))
                .thenReturn(false);

        when(jwtUtil.getCategory(accessToken))
                .thenReturn("not access");

        // when
        jwtFilter
                .doFilter(request,response,filterChain);

        // then
        assertThat(response
                .getStatus())
                .isEqualTo(401);

        verify(filterChain,never())
                .doFilter(request,response);

    }

    @Test
    @DisplayName("Redis에 UserAuthCache가 없을 시 , 401 응답 반환")
    void doFilterInternal_userAuthCacheNotFound()
            throws Exception{

        // given

        String accessToken = "accessToken";
        String username = "testUser@example.com";

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request
                .addHeader("Authorization","Bearer "+accessToken);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        when(jwtUtil
                .isExpired(accessToken))
                .thenReturn(false);

        when(jwtUtil
                .getCategory(accessToken))
                .thenReturn("access");

        when(jwtUtil
                .getUsername(accessToken))
                .thenReturn(username);

        when(userAuthCacheRepository
                .findByUsername(username))
                .thenReturn(Optional
                        .empty());

        // when
        jwtFilter
                .doFilter(request,response,filterChain);

        // then
        assertThat(response.getStatus())
                .isEqualTo(401);

        verify(filterChain,never())
                .doFilter(request,response);

    }

    @Test
    @DisplayName("유효한 access 토큰일 경우, Security Context에 Authorization을 저장한 후 다음 필터로")
    void doFilterInternal_success()
            throws Exception{

        // given

        String accessToken = "accessToken";
        String username = "testUser@example.com";
        String role = "ROLE_USER";

        UserAuthCache userAuthCache =
                mock(UserAuthCache.class);

        MockHttpServletRequest request =
                new MockHttpServletRequest();

        request
                .addHeader("Authorization","Bearer "+accessToken);

        MockHttpServletResponse response =
                new MockHttpServletResponse();

        when(jwtUtil
                .isExpired(accessToken))
                .thenReturn(false);

        when(jwtUtil
                .getCategory(accessToken))
                .thenReturn("access");

        when(jwtUtil
                .getUsername(accessToken))
                .thenReturn(username);

        when(userAuthCache
                .role())
                .thenReturn(role);

        when(userAuthCacheRepository
                .findByUsername(username))
                .thenReturn(Optional.of(userAuthCache));

        // when
        jwtFilter
                .doFilter(request,response,filterChain);

        // then
        Authentication authentication = SecurityContextHolder
                .getContext()
                .getAuthentication();

        assertThat(authentication)
                .isNotNull();

        assertThat(authentication
                .getPrincipal())
                .isNotNull();

        verify(filterChain,times(1))
                .doFilter(request,response);

    }



}