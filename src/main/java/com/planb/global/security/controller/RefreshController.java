package com.planb.global.security.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.planb.global.config.exception.dto.ApiResult;
import com.planb.global.security.dto.response.ReissueResponse;
import com.planb.global.security.facade.RefreshFacade;

@Tag(name = "토큰 재발급 API", description = "Refresh Token으로 인증 토큰을 재발급합니다.")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/refresh")
public class RefreshController {

    private final RefreshFacade refreshFacade;

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
                    """
    )
    public ResponseEntity<ApiResult<ReissueResponse>> reissue(HttpServletRequest request){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(refreshFacade
                                .reissue(request)));

    }
}
