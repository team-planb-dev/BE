package com.planb.global.security.util;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtUtil {

    private SecretKey secretKey;

    public JwtUtil
            (@Value("${jwt.secret}")String secret){

        secretKey = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),
                Jwts
                        .SIG
                        .HS256
                        .key()
                        .build()
                        .getAlgorithm());

    }

    public String getUsername(String token){

        return Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("username", String.class);
    }

    /**
     * 만료된 토큰에서도 username을 읽는다.
     *
     * 로그아웃 전용이다. 서명은 그대로 검증하므로 아무 문자열이나 통하지 않는다.
     * 토큰이 만료됐다는 이유로 서버에 남은 세션을 못 지우면 사용자가 갇힌다.
     */
    public String getUsernameAllowingExpired(String token){

        try {

            return getUsername(token);

        } catch (ExpiredJwtException e) {

            return e
                    .getClaims()
                    .get("username", String.class);
        }
    }

    public String getCategory(String token){
        return Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("category", String.class);
    }

    public Long getUserId(String token){

        return Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("userId", Long.class);
    }

    // 로그인마다 새로 발급되는 세션 식별자.
    // 같은 계정의 이전 세션 토큰을 가려내는 데만 쓴다.
    public String getSessionId(String token){

        return Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("sessionId", String.class);
    }

    public String getRole(String token){

        return Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .get("role", String.class);
    }

    public Boolean isExpired(String token){

        return Jwts
                .parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getExpiration()
                .before(new Date());
    }

    public String createJwt(
            String category,
            Long userId,
            String username,
            String role,
            String sessionId,
            Long expiredMs
    ){

        return Jwts.builder()
                .claim("category", category)
                .claim("userId", userId)
                .claim("username", username)
                .claim("role", role)
                .claim("sessionId", sessionId)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis()+expiredMs))
                .signWith(secretKey)
                .compact();

    }


}
