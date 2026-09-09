package com.planb.global.security.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReissueResponse(
        @Schema(description = "성공 응답의 재발급 상태이며 REFRESH_REISSUED입니다.")
        ReissueStatus status,
        @Schema(description = "재발급 처리 시각")
        String time,
        @Schema(description = "새 Access Token")
        String accessToken,
        @Schema(description = "새 Refresh Token")
        String refreshToken) {

    public enum ReissueStatus {
        REFRESH_REISSUED,
        REFRESH_NULL,
        REFRESH_EXPIRED

    }

}
