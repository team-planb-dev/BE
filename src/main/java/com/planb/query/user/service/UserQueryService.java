package com.planb.query.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.domain.user.entity.User;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.repository.UserAuthCacheRepository;
import com.planb.query.user.repository.UserQueryRepository;

@Service
@RequiredArgsConstructor
public class UserQueryService {

    private final UserQueryRepository userQueryRepository;
    private final UserAuthCacheRepository userAuthCacheRepository;

    // RDB에서 조회
    public User findByUsername(String username){

        return userQueryRepository
                .findByUsername(username)
                .orElseThrow(()-> new BaseException(BaseExceptionEnum
                        .USER_NOT_FOUND));

    }

    public User findById(Long id){

        return userQueryRepository
                .findById(id)
                .orElseThrow(()-> new BaseException(BaseExceptionEnum
                        .USER_NOT_FOUND));
    }

    // Redis에서 조회
    public UserAuthCache findByUsernameInCache(String username){

        return userAuthCacheRepository
                .findByUsername(username)
                .orElseThrow(()-> new BaseException(BaseExceptionEnum
                        .USER_NOT_FOUND));

    }
}
