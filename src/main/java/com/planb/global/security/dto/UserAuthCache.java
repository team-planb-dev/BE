package com.planb.global.security.dto;

public record UserAuthCache(Long userId,
                            String username,
                            String role,
                            String sessionId) {

    // 세션 식별자가 필요 없는 경로용.
    // UserDetailsService는 DB에서 회원을 읽을 뿐이라 발급된 세션을 알지 못한다.
    public UserAuthCache(
            Long userId,
            String username,
            String role
    ) {

        this(userId, username, role, null);
    }
}
