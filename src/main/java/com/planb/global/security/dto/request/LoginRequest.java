package com.planb.global.security.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record LoginRequest(
        @Schema(description = "가입한 이메일", example = "user@example.com")
        String username,

        @Schema(description = "계정 비밀번호", example = "Password1!")
        String password
) {
}
