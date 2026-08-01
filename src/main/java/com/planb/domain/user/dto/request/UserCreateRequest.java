package com.planb.domain.user.dto.request;

public record UserCreateRequest(String username,
                                String nickname,
                                String password) {
}
