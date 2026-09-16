package com.planb.global.security.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.planb.global.config.exception.dto.ApiResult;
import com.planb.global.security.dto.response.ReissueResponse;
import com.planb.global.security.facade.RefreshFacade;
import com.planb.global.utils.web.CookieUtil;

@Tag(name = "토큰 재발급 API", description = "Refresh Token으로 인증 토큰을 재발급합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/refresh")
public class RefreshController {

    private final RefreshFacade refreshFacade;
    private final CookieUtil cookieUtil;

    @PostMapping("/reissue")
    @Operation(
            summary = "인증 토큰 재발급",
            description = """
                    `refreshToken` HttpOnly Cookie를 기준으로 Access Token과 Refresh Token을 재발급합니다.
                    성공하면 `status=REFRESH_REISSUED`와 새 토큰을 반환합니다.
                    Cookie가 없으면 `BASE.EXCEPTION.REFRESH_TOKEN_EXPIRED`를 반환합니다.
                    Cookie가 만료되었거나 서버에 저장된 Refresh Token이 없으면
                    `BASE.EXCEPTION.REFRESH_TOKEN_NOT_FOUND`를 반환합니다.
                    이 비즈니스 오류들은 현재 HTTP 200과 `success=false`로 반환됩니다.
                    재발급 시 새 Refresh Token을 `Set-Cookie`로 함께 내려 줍니다.
                    """
    )
    public ResponseEntity<ApiResult<ReissueResponse>> reissue(
            HttpServletRequest request,
            HttpServletResponse response
    ){

        ReissueResponse reissueResponse = refreshFacade
                .reissue(request);

        // 재발급은 서버에 저장된 옛 Refresh Token을 지운다.
        // 쿠키를 갱신하지 않으면 브라우저가 지워진 토큰을 계속 보내 다음 재발급에서 로그아웃된다.
        response.addCookie(
                cookieUtil.createCookie(
                        "refreshToken",
                        reissueResponse.refreshToken()
                )
        );

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(reissueResponse));

    }
}
