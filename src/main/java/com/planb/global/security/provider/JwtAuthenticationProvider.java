package com.planb.global.security.provider;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.auth.AuthPrincipal;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.repository.UserAuthCacheRepository;
import com.planb.global.security.util.JwtUtil;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationProvider {

    private final JwtUtil jwtUtil;
    private static final String ACCESS_TOKEN_CATEGORY = "access";

    private final UserAuthCacheRepository userAuthCacheRepository;

    public Authentication authenticate(String authorizationHeader){

        // Bearer 접두사 확인 및 토큰 추출
        String parsedToken = extractToken(authorizationHeader);

        // 토큰 만료 여부 확인
        handleExpiredAuthorization(parsedToken);

        // access 토큰 카테고리 확인
        checkAccessTokenCategory(parsedToken);

        // username 추출
        String username = jwtUtil.getUsername(parsedToken);

        // Redis Cache에 존재하는 유저인지 확인
        UserAuthCache userAuthCache = userAuthCacheRepository
                .findByUsername(username)
                .orElseThrow(()->new BaseException(BaseExceptionEnum.USER_NOT_FOUND));

        AuthPrincipal authPrincipal =
                new AuthPrincipal(userAuthCache);

        // Authorization 객체 반환
        return new UsernamePasswordAuthenticationToken(
                authPrincipal,
                null,
                authPrincipal
                        .getAuthorities());

    }



    // Bearer 접두사 확인 및 토큰 추출
    private String extractToken(String authorizationHeader){

        if (authorizationHeader == null
                || authorizationHeader.isBlank()
                || !authorizationHeader.startsWith("Bearer ")) {

            throw new BaseException(
                    BaseExceptionEnum.INVALID_TOKEN
            );
        }

        return authorizationHeader
                .substring(7);
    }

    // 토큰 만료 여부확인 처리 메소드
    private void handleExpiredAuthorization(String parsedToken){

        if(jwtUtil.isExpired(parsedToken)){
            throw new BaseException(BaseExceptionEnum.JWT_EXPIRED);
        }

    }

    private void checkAccessTokenCategory(String accessToken){
        String category = jwtUtil.getCategory(accessToken);

        if (!ACCESS_TOKEN_CATEGORY.equals(category)) {
            throw new BaseException(
                    BaseExceptionEnum.INVALID_TOKEN_CATEGORY
            );
        }
    }


}
