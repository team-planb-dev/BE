package com.planb.global.security.filter;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.dto.ApiResult;
import com.planb.global.security.dto.response.FilterSuccessResponse;
import com.planb.global.security.service.RefreshService;
import com.planb.global.security.service.UserAuthCacheService;
import com.planb.global.security.util.JwtUtil;
import com.planb.global.security.validator.RefreshTokenValidator;
import com.planb.global.utils.app.JsonResponseUtils;
import com.planb.global.utils.web.CookieUtil;

import java.io.IOException;
import java.time.LocalDate;

@RequiredArgsConstructor
@Slf4j
public class JwtLogoutFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final RefreshService refreshService;
    private final UserAuthCacheService userAuthCacheService;
    private final CookieUtil cookieUtil;
    private final RefreshTokenValidator refreshTokenValidator;

    @Override
    protected boolean shouldNotFilter
            (HttpServletRequest request)
            throws ServletException {
        return !("/logout".equals(request.getRequestURI())
                &&"POST".equalsIgnoreCase(request.getMethod()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String refresh = cookieUtil.findCookie(request);

        // refresh 쿠키는 브라우저 설정·시크릿 모드·만료로 흔하게 사라진다.
        // 그때 아무것도 안 하면 서버 세션이 남아 다음 로그인이 옛 세션을 이어받지 못한다.
        if (refreshTokenValidator.isInvalid(refresh)){
            logoutByAccessToken(request, response);
            return;
        }

        String username = jwtUtil.getUsername(refresh);

        // Refresh 삭제하기
        refreshService.deleteRefresh(refresh);

        userAuthCacheService.deleteUserAuthCache(username);

        // Cookie를 빈 쿠키로 설정
        response.addCookie(cookieUtil.zeroCookie(response));

        // 로그아웃 성공 Response반환
        JsonResponseUtils.writeJsonResponse(HttpStatus
                        .OK,
                response,
                new FilterSuccessResponse(true,
                        "Method : /logout ",
                        "로그아웃에 성공하였습니다.",
                        LocalDate
                                .now()
                                .toString()));

        log.info("[ 회원 로그아웃 ] : {}", username);
    }

    private void logoutByAccessToken(HttpServletRequest request,
                                     HttpServletResponse response)
            throws IOException {

        String username = usernameFromAccessToken(request);

        if (username == null) {

            JsonResponseUtils.writeJsonResponse(HttpStatus.UNAUTHORIZED,
                    response,
                    ApiResult.fail(BaseExceptionEnum.LOGOUT_CREDENTIAL_NOT_FOUND));

            return;
        }

        refreshService.deleteRefreshByUsername(username);

        userAuthCacheService.deleteUserAuthCache(username);

        response.addCookie(cookieUtil.zeroCookie(response));

        JsonResponseUtils.writeJsonResponse(HttpStatus.OK,
                response,
                new FilterSuccessResponse(true,
                        "Method : /logout ",
                        "로그아웃에 성공하였습니다.",
                        LocalDate
                                .now()
                                .toString()));

        log.info("[ 회원 로그아웃 - refresh 쿠키 없음 ] : {}", username);
    }

    private String usernameFromAccessToken(HttpServletRequest request) {

        String header = request.getHeader("Authorization");

        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }

        try {

            return jwtUtil.getUsernameAllowingExpired(header.substring(7));

        } catch (JwtException e) {

            return null;
        }
    }
}
