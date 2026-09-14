package com.planb.global.security.util;

/**
 * 토큰과 인증 캐시의 수명.
 *
 * access 토큰과 인증 캐시는 같은 값을 쓴다. 둘 중 하나만 살아 있는 구간이 생기면
 * 토큰은 유효한데 인증은 실패하는 상태가 되기 때문이다.
 */
public final class TokenExpiration {

    public static final Long ACCESS_TOKEN_EXPIRED_MS = 600000 * 6 * 24L;

    public static final Long REFRESH_TOKEN_EXPIRED_MS = 7 * 600000 * 6 * 24L;

    private TokenExpiration() {
    }
}
