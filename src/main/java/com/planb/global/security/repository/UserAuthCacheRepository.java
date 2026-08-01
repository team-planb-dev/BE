package com.planb.global.security.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;
import com.planb.global.security.dto.UserAuthCache;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class UserAuthCacheRepository  {

    private final RedisTemplate<String,UserAuthCache> userAuthredisTemplate;

    public void save(String username,
                     UserAuthCache userAuthCache,
                     Long expiredMs){

        userAuthredisTemplate
                .opsForValue()
                .set(
                        "security:auth:user:" + username,
                        userAuthCache,
                        expiredMs,
                        TimeUnit.MILLISECONDS

                );
    }

    public Optional<UserAuthCache> findByUsername(String username){

        UserAuthCache userAuthCache =  userAuthredisTemplate
                .opsForValue()
                .get("security:auth:user:"+username);

        return Optional.ofNullable(userAuthCache);

    }
}
