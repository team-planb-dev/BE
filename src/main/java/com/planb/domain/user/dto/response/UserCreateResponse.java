package com.planb.domain.user.dto.response;

import java.time.Instant;
import java.time.LocalDateTime;

public record UserCreateResponse(String username,
                                 Instant createdAt,
                                 Instant updatedAt) {
}
