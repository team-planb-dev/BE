package com.planb.global.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.repository.UserAuthCacheRepository;
import com.planb.global.security.util.TokenExpiration;

@Service
@RequiredArgsConstructor
public class UserAuthCacheService {

    private final UserAuthCacheRepository userAuthCacheRepository;

    // Redis에 UserAuthCache 저장
    // 토큰 유효 기간 중 인증 실패 방지를 위한 access 토큰·캐시 수명 일치
    public void saveUserAuthCache(UserAuthCache userAuthCache) {
        userAuthCacheRepository
                .save(
                userAuthCache.username(),
                userAuthCache,
                TokenExpiration.ACCESS_TOKEN_EXPIRED_MS
        );
    }

    public void deleteUserAuthCache(String username) {

        userAuthCacheRepository.delete(username);
    }
}
