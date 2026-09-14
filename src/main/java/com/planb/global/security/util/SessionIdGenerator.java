package com.planb.global.security.util;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 로그인 한 번을 가리키는 식별자를 만든다.
 *
 * 전역 유일까지는 필요 없다. 같은 계정의 직전 세션과만 달라지면 되므로 8바이트로 충분하다.
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
