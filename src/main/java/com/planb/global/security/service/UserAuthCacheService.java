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

    // Redis에 UserAuthCache 저장.
    // access 토큰과 수명을 맞춘다. 캐시가 먼저 죽으면 토큰은 유효한데 인증만 실패한다.
    public void saveUserAuthCache(UserAuthCache userAuthCache){
        userAuthCacheRepository
                .save(userAuthCache.username(),
                        userAuthCache,
                        TokenExpiration.ACCESS_TOKEN_EXPIRED_MS);
    }

    public void deleteUserAuthCache(String username) {

        userAuthCacheRepository.delete(username);
    }
}
