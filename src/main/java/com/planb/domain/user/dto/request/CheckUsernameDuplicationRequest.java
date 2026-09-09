package com.planb.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record CheckUsernameDuplicationRequest(
        @Schema(description = "중복을 확인할 이메일", example = "user@example.com")
        String username
) {
}
