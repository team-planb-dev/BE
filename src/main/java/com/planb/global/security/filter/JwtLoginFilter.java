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

    public JwtLoginFilter(ObjectMapper objectMapper,
                          AuthenticationManager authenticationManager,
                          JwtUtil jwtUtil,
                          RefreshService refreshService,
                          UserAuthCacheService userAuthCacheService,
                          CookieUtil cookieUtil){

        this.objectMapper = objectMapper;
        this.authenticationManager = authenticationManager;
        this.jwtUtil = jwtUtil;
        this.refreshService = refreshService;
        this.cookieUtil = cookieUtil;
        this.userAuthCacheService = userAuthCacheService;
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

            // 중복 로그인 체크
            refreshService.validateAlreadyLogin(username);

            UsernamePasswordAuthenticationToken authenticationToken =
                    new UsernamePasswordAuthenticationToken(username,loginRequest.password());

            return authenticationManager.authenticate(authenticationToken);

        }catch(IOException e){
            throw new AuthenticationServiceException("JSON 파싱 오류",e);
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

        // access 토큰 생성
        String access = jwtUtil.createJwt("access",username,role,600000*6*24L);

        // refresh 토큰 생성
        String refresh = jwtUtil.createJwt("refresh",username,role,7*600000*6*24L);

        UserAuthCache userAuthCache = new UserAuthCache(
                userId,
                username,
                role);

        // User의 간단한 정보를 담은 DTO를 Redis에 저장
        userAuthCacheService.saveUserAuthCache(userAuthCache);

        // cache에 refresh 토큰 추가
        refreshService.addRefresh(username,refresh);

        log.info("로그인 성공:{} " + " [ Time ]:{}",username, LocalDate.now());

        response.setHeader("Authorization", "Bearer " + access);
        response.addCookie(cookieUtil.createCookie("refreshToken",refresh));

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
                .fail("AUTH_FAILED","아이디 또는 비밀번호가 일치하지 않습니다.");

        JsonResponseUtils.writeJsonResponse(
                HttpStatus.UNAUTHORIZED,
                response,
                result
        );

    }
}
