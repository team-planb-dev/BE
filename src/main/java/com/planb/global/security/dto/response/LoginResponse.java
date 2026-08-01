package com.planb.global.security.dto.response;

public record LoginResponse(String username,
                            String message,
                            String loginAt) {
}
