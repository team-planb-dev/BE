package com.planb.global.security.service;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.dto.response.ReissueResponse;
import com.planb.global.security.repository.UserTokenCacheRepository;
import com.planb.global.security.util.TokenExpiration;
import com.planb.global.security.util.JwtUtil;
import com.planb.global.utils.web.CookieUtil;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefreshService {

    private final JwtUtil jwtUtil;
    private final UserTokenCacheRepository userTokenCacheRepository;
    private final CookieUtil cookieUtil;
    private final UserAuthCacheService userAuthCacheService;

    public ReissueResponse refreshCookies(HttpServletRequest request){

        String refresh = cookieUtil.findCookie(request);

        // refresh가 비엇는지 검사
        if (refresh == null) {
            return handleRefreshTokenNull();
        }

        // refresh가 파기되었는지 검사
        try{
            jwtUtil.isExpired(refresh);

        } catch (ExpiredJwtException e) {
            return handleRefreshTokenExpired();
        }

        if (!userTokenCacheRepository
                .exists("refresh:refreshToken:" + refresh)) {

            throw new BaseException(
                    BaseExceptionEnum.REFRESH_TOKEN_NOT_FOUND
            );
        }

        // 인증 캐시는 로그인에서만 저장되므로 access 토큰보다 먼저 사라진다.
        // 여기서 같은 세션으로 다시 채우지 않으면 새 access 토큰도 곧바로 거부된다.
        restoreUserAuthCache(refresh);

        return new ReissueResponse(ReissueResponse.ReissueStatus.REFRESH_REISSUED,
                LocalDateTime
                        .now()
                        .toString(),
                resetAccessToken(request),
                reissueRefresh(request));
    }

    private ReissueResponse handleRefreshTokenNull(){
        return new ReissueResponse(ReissueResponse.ReissueStatus.REFRESH_NULL,
                LocalDateTime
                        .now()
                        .toString(),
                null,
                null);
    }

    private ReissueResponse handleRefreshTokenExpired(){
        return new ReissueResponse(ReissueResponse.ReissueStatus.REFRESH_EXPIRED,
                LocalDateTime
                        .now()
                        .toString(),
                null,
                null);
    }

    private void restoreUserAuthCache(String refresh){

        userAuthCacheService.saveUserAuthCache(new UserAuthCache(
                jwtUtil.getUserId(refresh),
                jwtUtil.getUsername(refresh),
                jwtUtil.getRole(refresh),
                jwtUtil.getSessionId(refresh)));
    }

    // access 토큰을 초기화 하는 메소드
    private String resetAccessToken
    (HttpServletRequest request){

        String refresh = cookieUtil.findCookie(request);

        String username = jwtUtil.getUsername(refresh);
        String role = jwtUtil.getRole(refresh);

        return jwtUtil
                .createJwt("access",
                        jwtUtil.getUserId(refresh),
                        username,
                        role,
                        jwtUtil.getSessionId(refresh),
                        TokenExpiration.ACCESS_TOKEN_EXPIRED_MS);
    }

    private String reissueRefresh
            (HttpServletRequest request){

        String refresh = cookieUtil.findCookie(request);

        String username = jwtUtil.getUsername(refresh);

        String newRefresh = jwtUtil
                .createJwt("refresh",
                        jwtUtil.getUserId(refresh),
                        username,
                        jwtUtil.getRole(refresh),
                        jwtUtil.getSessionId(refresh),
                        TokenExpiration.REFRESH_TOKEN_EXPIRED_MS);

        deleteRefresh(refresh);

        addRefresh(username, newRefresh);

        return newRefresh;

    }

    public void addRefresh(String username,
                           String refresh){

        userTokenCacheRepository
                .save("refresh:user:"+username,
                        refresh,
                        TokenExpiration.REFRESH_TOKEN_EXPIRED_MS);

        userTokenCacheRepository
                .save("refresh:refreshToken:"+refresh,
                        username,
                        TokenExpiration.REFRESH_TOKEN_EXPIRED_MS);

    }

    public void deleteRefresh(String refresh){

        String username = jwtUtil.getUsername(refresh);

        if (username == null){
            return;
        }

        userTokenCacheRepository
                .delete("refresh:refreshToken:"+refresh);

        userTokenCacheRepository
                .delete("refresh:user:"+username);
    }

    public void deleteRefreshByUsername(String username) {

        String userKey = "refresh:user:" + username;

        if (!userTokenCacheRepository.exists(userKey)) {
            return;
        }

        String refresh = (String) userTokenCacheRepository
                .findByKey(userKey);

        userTokenCacheRepository.delete(
                "refresh:refreshToken:" + refresh
        );

        userTokenCacheRepository.delete(userKey);
    }





}
