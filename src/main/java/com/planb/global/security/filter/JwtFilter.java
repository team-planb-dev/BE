package com.planb.global.security.filter;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.dto.ApiResult;
import com.planb.global.security.auth.AuthPrincipal;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.repository.UserAuthCacheRepository;
import com.planb.global.security.util.JwtUtil;
import com.planb.global.utils.app.JsonResponseUtils;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Optional;

@RequiredArgsConstructor
@Slf4j
public class JwtFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserAuthCacheRepository userAuthCacheRepository;

    // async 재진입(asyncDispatch) 시에도 JWT를 다시 검증해야 STATELESS 세션 정책에서
    // SecurityContext가 끊기지 않는다 (OncePerRequestFilter 기본값은 async dispatch를 건너뜀)
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal
            (HttpServletRequest request,
             HttpServletResponse response,
             FilterChain filterChain)
            throws ServletException, IOException {

        String accessToken = extractToken(request);

        // 빈 토큰 여부 검사
        if(accessToken == null){
            log.info("No Access Token: {}", LocalDateTime.now());
            filterChain.doFilter(request,response);
            return;
        }

        // 토큰 파기여부 검사
        try{

            jwtUtil.isExpired(accessToken);

        } catch (ExpiredJwtException e){

            handleExpiredJwt(response);
            return;
        }

        // 토큰 카테고리 검사
        if(!checkTokenCategory(jwtUtil.getCategory(accessToken))){
            log.info("Token Invalid Category: {}",LocalDateTime.now());
            handleInvalidTokenCategory(response);
            return;
        }

        // Redis Cache에서 회원정보 조회

        Authentication authentication = makeAuthentication(accessToken,response);
        if(authentication==null){
            return;
        }

        SecurityContextHolder
                .getContext()
                .setAuthentication(authentication);

        filterChain.doFilter(request,response);

    }

    private String extractToken(HttpServletRequest request){
        String header = request.getHeader("Authorization");
        if(header == null || !header.startsWith("Bearer ")){
            return null;
        }
        return header.substring(7);
    }

    private void handleExpiredJwt
            (HttpServletResponse httpServletResponse)
            throws IOException{

        ApiResult<?> result = ApiResult.fail(BaseExceptionEnum.JWT_EXPIRED);

        JsonResponseUtils
                .writeJsonResponse(HttpStatus
                                .UNAUTHORIZED,
                        httpServletResponse,
                        result);
    }

    private void handleInvalidTokenCategory
            (HttpServletResponse httpServletResponse)
            throws IOException{
        ApiResult<?> result = ApiResult.fail(BaseExceptionEnum.INVALID_TOKEN_CATEGORY);

        JsonResponseUtils
                .writeJsonResponse(HttpStatus
                                .UNAUTHORIZED,
                        httpServletResponse,
                        result);
    }

    private void handleRedisMissToken
            (HttpServletResponse httpServletResponse)
            throws IOException{
        ApiResult<?> result = ApiResult.fail(BaseExceptionEnum.ENTITY_NOT_FOUND);

        JsonResponseUtils
                .writeJsonResponse(HttpStatus.UNAUTHORIZED,
                        httpServletResponse,
                        result);
    }

    private boolean checkTokenCategory
            (String category){
        return "access".equals(category);
    }

    private Authentication makeAuthentication(String token,
                                              HttpServletResponse httpServletResponse)
            throws IOException {

        String username = jwtUtil.getUsername(token);

        Optional<UserAuthCache> userAuthCache = userAuthCacheRepository
                .findByUsername(username);

        if(userAuthCache.isEmpty()){
            handleRedisMissToken(httpServletResponse);
            return null;
        }

        AuthPrincipal authPrincipal = new AuthPrincipal(userAuthCache.get());

        return new UsernamePasswordAuthenticationToken(
                authPrincipal,
                null,
                authPrincipal.getAuthorities()
        );

    }
}
