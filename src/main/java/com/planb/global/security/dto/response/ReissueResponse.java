package com.planb.global.security.dto.response;

public record ReissueResponse(
        ReissueStatus status,
        String time,
        String accessToken,
        String refreshToken) {

    public enum ReissueStatus {
        REFRESH_REISSUED,
        REFRESH_NULL,
        REFRESH_EXPIRED

    }

}
