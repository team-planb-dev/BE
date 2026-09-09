package com.planb.domain.user.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record CheckNicknameDuplicationRequest(
        @Schema(description = "중복을 확인할 닉네임", example = "planb_user")
        String nickname
) {
}
