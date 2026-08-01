package com.planb.global.security.dto;

public record UserAuthCache(Long userId,
                            String username,
                            String role) {
}
