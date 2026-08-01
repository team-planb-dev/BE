package com.planb.global.security.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;
import com.planb.global.security.dto.response.FilterSuccessResponse;
import com.planb.global.security.service.RefreshService;
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
        String username = jwtUtil.getUsername(refresh);

        // refresh 토큰 오류검사
        if(refreshTokenValidator.isInvalid(refresh)){
            filterChain.doFilter(request,response);
            return;
        }

        // Refresh 삭제하기
        refreshService.deleteRefresh(refresh);

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

        log.info("[ 회원 로그아웃 ] : {}",username);
    }

    
}
