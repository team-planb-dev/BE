package com.planb.global.docs.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.planb.global.config.exception.dto.ApiResult;
import com.planb.global.security.dto.request.LoginRequest;
import com.planb.global.security.dto.response.FilterSuccessResponse;
import com.planb.global.security.dto.response.LoginResponse;

import java.time.LocalDate;

@Tag(
        name = "인증 API 문서",
        description = "Security Filter가 처리하는 로그인과 로그아웃의 요청 및 응답 형식을 안내합니다."
)
@RestController
@RequestMapping("/docs/api")
public class ApiDocsController {

    @Operation(
            summary = "사용자 로그인",
            description = """
                    문서화를 위한 Mock API이며 실제 로그인 요청 경로는 `POST /login`입니다.
                    본문에 이메일(username)과 비밀번호(password)를 전달해 주세요.
                    성공하면 Access Token을 `Authorization` 응답 헤더로 반환하고,
                    Refresh Token을 `refreshToken` HttpOnly Cookie로 설정합니다.
                    인증 정보가 일치하지 않으면 HTTP 401과 `AUTH_FAILED` 오류를 반환합니다.
                    """,
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    description = "실제 `POST /login` 요청 본문",
                    content = @Content(
                            schema = @Schema(
                                    implementation = LoginRequest.class
                            )
                    )
            )
    )
    @PostMapping("/login")
    public ResponseEntity<ApiResult<LoginResponse>> login(){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(new LoginResponse("test1",
                                "로그인에 성공하였습니다.",
                                LocalDate
                                        .now()
                                        .toString())));

    }

    @Operation(
            summary = "사용자 로그아웃",
            description = """
                    문서화를 위한 Mock API이며 실제 로그아웃 요청 경로는 `POST /logout`입니다.
                    로그인 시 발급받은 `refreshToken` HttpOnly Cookie를 함께 전송해 주세요.
                    성공하면 서버의 Refresh Token과 인증 캐시를 삭제하고 해당 Cookie를 만료시킵니다.
                    """
    )
    @PostMapping("/logout")
    public ResponseEntity<FilterSuccessResponse> logout(){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(new FilterSuccessResponse(true,
                        "Method : /logout ",
                        "로그아웃에 성공하였습니다.",
                        LocalDate
                                .now()
                                .toString()));
    }
}
