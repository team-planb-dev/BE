package com.planb.global.docs.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.planb.global.config.exception.dto.ApiResult;
import com.planb.global.security.dto.response.LoginResponse;

import java.time.LocalDateTime;

@Tag(name = "Docks", description = "Controller 형태가 아닌 API를 위한 문서")
@RestController
@RequestMapping("/docs/api")
public class ApiDocsController {

    @Operation(summary = "사용자 로그인",description = "로그인 API, 로그인 url은 {baseURL}/login 입니다.")
    @PostMapping("/login")
    public ResponseEntity<ApiResult<LoginResponse>> login(){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(new LoginResponse("test1",
                                "로그인에 성공하였습니다.",
                                LocalDateTime
                                        .now()
                                        .toString())));

    }
}
