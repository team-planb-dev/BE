package com.planb.global.security.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import com.planb.global.config.exception.dto.ApiResult;
import com.planb.global.security.auth.UserDetailsImpl;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.dto.request.LoginRequest;
import com.planb.global.security.dto.response.LoginResponse;
import com.planb.global.security.service.RefreshService;
import com.planb.global.security.service.UserAuthCacheService;
import com.planb.global.security.util.JwtUtil;
import com.planb.global.security.util.SessionIdGenerator;
import com.planb.global.security.util.TokenExpiration;
import com.planb.global.utils.app.JsonResponseUtils;
import com.planb.global.utils.web.CookieUtil;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;


@Slf4j
public class JwtLoginFilter extends UsernamePasswordAuthenticationFilter {

    private final ObjectMapper objectMapper;
    private final AuthenticationManager authenticationManager;
    private final JwtUtil jwtUtil;
    private final RefreshService refreshService;
    private final UserAuthCacheService userAuthCacheService;
    private final CookieUtil cookieUtil;
    private final SessionIdGenerator sessionIdGenerator;

    public JwtLoginFilter(ObjectMapper objectMapper,
                          AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          RefreshService refreshService,
                          UserAuthCacheService userAuthCacheService,
                          CookieUtil cookieUtil,
                          SessionIdGenerator sessionIdGenerator){

        this.objectMapper = objectMapper;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.refreshService = refreshService;
        this.cookieUtil = cookieUtil;
        this.userAuthCacheService = userAuthCacheService;
        this.sessionIdGenerator = sessionIdGenerator;
        setAuthenticationManager(authenticationManager);
    }

    @Override
    public Authentication attemptAuthentication
            (HttpServletRequest request,
             HttpServletResponse response)
            throws AuthenticationException {

        try{

            InputStream inputStream = request.getInputStream();

            LoginRequest loginRequest = objectMapper
                    .readValue(inputStream, LoginRequest.class);

            String username = loginRequest.username();

            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(username, loginRequest.password());

            return authenticationManager.authenticate(authenticationToken);

        } catch (IOException e){
            throw new AuthenticationServiceException("JSON 파싱 오류", e);
        }

    }

    @Override
    protected void successfulAuthentication
            (HttpServletRequest request,
             HttpServletResponse response,
             FilterChain chain,
             Authentication authResult)
            throws IOException, ServletException {

        String role = authResult.getAuthorities()
                .stream()
                .findFirst()
                .map(GrantedAuthority::getAuthority)
                .orElseThrow();



        UserDetailsImpl userDetails = (UserDetailsImpl) authResult.getPrincipal();

        Long userId = userDetails.getUserId();
        String username = userDetails.getUsername();

        // 이번 로그인을 직전 로그인과 구분하는 식별자.
        // 두 토큰과 캐시에 같은 값이 들어가야 JwtFilter가 옛 세션을 가려낸다.
        String sessionId = sessionIdGenerator.generate();

        // access 토큰 생성
        String access = jwtUtil.createJwt(
                "access",
                userId,
                username,
                role,
                sessionId,
                TokenExpiration.ACCESS_TOKEN_EXPIRED_MS);

        // refresh 토큰 생성
        String refresh = jwtUtil.createJwt(
                "refresh",
                userId,
                username,
                role,
                sessionId,
                TokenExpiration.REFRESH_TOKEN_EXPIRED_MS);

        UserAuthCache userAuthCache = new UserAuthCache(
                userId,
                username,
                role,
                sessionId);

        // User의 간단한 정보를 담은 DTO를 Redis에 저장
        userAuthCacheService.saveUserAuthCache(userAuthCache);

        // 같은 계정의 이전 세션을 끊는다. 비밀번호가 맞은 뒤에만 해야
        // 남의 계정에 틀린 비밀번호를 넣어 로그아웃시키는 일이 생기지 않는다.
        refreshService.deleteRefreshByUsername(username);

        // cache에 refresh 토큰 추가
        refreshService.addRefresh(username, refresh);

        log.info("로그인 성공:{} " + " [ Time ]:{}", username, LocalDate.now());

        response.setHeader("Authorization", "Bearer " + access);
        response.addCookie(cookieUtil.createCookie("refreshToken", refresh));

        ApiResult<?> result = ApiResult
                .success(new LoginResponse(username,
                        "로그인에 성공하였습니다.",
                        LocalDate
                                .now()
                                .toString()));

        JsonResponseUtils
                .writeJsonResponse(HttpStatus
                        .OK,
                        response,
                        result);

    }

    @Override
    protected void unsuccessfulAuthentication
            (HttpServletRequest request,
             HttpServletResponse response,
             AuthenticationException failed)
            throws IOException, ServletException {

        ApiResult<?> result = ApiResult
                .fail("AUTH_FAILED", "아이디 또는 비밀번호가 일치하지 않습니다.");

        JsonResponseUtils.writeJsonResponse(
                HttpStatus.UNAUTHORIZED,
                response,
                result
        );

    }
}
