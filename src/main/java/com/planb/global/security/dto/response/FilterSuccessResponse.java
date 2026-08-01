package com.planb.global.security.dto.response;

public record FilterSuccessResponse(boolean success,
                                   String method,
                                   String message,
                                   String time) {
}
