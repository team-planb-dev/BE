package com.planb.domain.user.dto.response;

import com.planb.domain.user.entity.User;

/**
 * 마이페이지에 표시할 회원 정보
 */
public record UserReadResponse(Long userId,
                               String username,
                               String nickname,
                               String role) {

    public static UserReadResponse from(User user) {

        return new UserReadResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole()
        );
    }
}
