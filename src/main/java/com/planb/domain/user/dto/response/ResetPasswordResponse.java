package com.planb.domain.user.dto.response;

import java.time.Instant;

public record ResetPasswordResponse(

        String username,
        Instant resetAt) {
}
