package com.planb.global.security.service;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.dto.response.ReissueResponse;
import com.planb.global.security.repository.UserTokenCacheRepository;
import com.planb.global.security.util.JwtUtil;
import com.planb.global.utils.web.CookieUtil;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefreshService {

    private final JwtUtil jwtUtil;
    private final UserTokenCacheRepository userTokenCacheRepository;
    private final CookieUtil cookieUtil;

    public ReissueResponse refreshCookies(HttpServletRequest request){

        String refresh = cookieUtil.findCookie(request);

        // refresh가 비엇는지 검사
        if(refresh == null) {
            return handleRefreshTokenNull();
        }

        // refresh가 파기되었는지 검사
        try{
            jwtUtil.isExpired(refresh);

         } catch(ExpiredJwtException e) {
            return handleRefreshTokenExpired();
        }

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

    // access 토큰을 초기화 하는 메소드
    private String resetAccessToken
    (HttpServletRequest request){

        String refresh = cookieUtil.findCookie(request);

        String username = jwtUtil.getUsername(refresh);
        String role = jwtUtil.getRole(refresh);

        return jwtUtil
                .createJwt("access",
                        username,
                        role,
                        600000*6*24L);
    }

    private String reissueRefresh
            (HttpServletRequest request){

        String refresh = cookieUtil.findCookie(request);

        String username = jwtUtil.getUsername(refresh);

        String newRefresh = jwtUtil
                .createJwt("refresh",
                        username,
                        jwtUtil
                                .getRole(refresh),
                        7*600000*6*24L);

        deleteRefresh(refresh);

        addRefresh(username,newRefresh);

        return newRefresh;

    }

    public void addRefresh(String username,
                           String refresh){

        userTokenCacheRepository
                .save("refresh:user:"+username,
                        refresh,
                        7*600000*6*24L);

        userTokenCacheRepository
                .save("refresh:refreshToken:"+refresh,
                        username,
                        7*600000*6*24L);

    }

    public void deleteRefresh(String refresh){

        String username = jwtUtil.getUsername(refresh);

        if(username == null){
            return;
        }

        userTokenCacheRepository
                .delete("refresh:refreshToken:"+refresh);

        userTokenCacheRepository
                .delete("refresh:user:"+username);
    }


    public void validateAlreadyLogin(String username){

        if(userTokenCacheRepository.exists("refresh:user:" + username)){
            throw new BaseException(BaseExceptionEnum.USER_ALREADY_LOGIN);
        }
    }




}
