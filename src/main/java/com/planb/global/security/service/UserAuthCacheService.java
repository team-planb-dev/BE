package com.planb.global.security.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.repository.UserAuthCacheRepository;

@Service
@RequiredArgsConstructor
public class UserAuthCacheService {

    private final UserAuthCacheRepository userAuthCacheRepository;

    // Redis에 UserAuthCache 저장
    public void saveUserAuthCache(UserAuthCache userAuthCache){
        userAuthCacheRepository
                .save(userAuthCache.username(), userAuthCache,2_100_000L);
    }
}
