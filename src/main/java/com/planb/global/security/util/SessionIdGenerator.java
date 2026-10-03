package com.planb.global.security.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 로그인 세션 식별자 생성
 */
@Component
public class SessionIdGenerator {

    private static final int SESSION_ID_BYTES = 8;

    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {

        byte[] bytes = new byte[SESSION_ID_BYTES];

        secureRandom.nextBytes(bytes);

        return Base64
                .getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
