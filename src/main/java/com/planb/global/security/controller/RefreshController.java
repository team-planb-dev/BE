package com.planb.global.security.controller;

import io.swagger.v3.oas.annotations.Operation;
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

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/refresh")
public class RefreshController {

    private final RefreshFacade refreshFacade;

    @PostMapping("/reissue")
    @Operation(summary = "refresh 토큰 갱신",
            description = "사용자의 refresh 토큰을 갱신합니다.")
    public ResponseEntity<ApiResult<ReissueResponse>> reissue(HttpServletRequest request){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(refreshFacade
                                .reissue(request)));

    }
}
