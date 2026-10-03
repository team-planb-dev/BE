package com.planb.global.security.dto;

public record UserAuthCache(Long userId,
                            String username,
                            String role,
                            String sessionId) {

    // 세션 식별자가 필요 없는 경로용
    // DB 회원 조회만 수행하는 UserDetailsService의 세션 정보 부재
    public UserAuthCache(
            Long userId,
            String username,
            String role
    ) {

        this(
                userId,
                username,
                role,
                null
        );
    }
}
