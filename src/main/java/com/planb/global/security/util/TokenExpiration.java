package com.planb.global.security.util;

/**
 * 토큰과 인증 캐시의 공통 만료 시간
 */
public final class TokenExpiration {

    public static final Long ACCESS_TOKEN_EXPIRED_MS = 600000 * 6 * 24L;

    public static final Long REFRESH_TOKEN_EXPIRED_MS = 7 * 600000 * 6 * 24L;

    private TokenExpiration() {
    }
}
