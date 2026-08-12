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

    /*
     RDB에서 조회
     */

    // username으로 객체 조회
    public User findByUsername(String username){

        return userQueryRepository
                .findByUsername(username)
                .orElseThrow(()-> new BaseException(BaseExceptionEnum
                        .USER_NOT_FOUND));

    }

    // id로 객체 조회
    public User findById(Long id){

        return userQueryRepository
                .findById(id)
                .orElseThrow(()-> new BaseException(BaseExceptionEnum
                        .USER_NOT_FOUND));
    }

    // username으로 중복 여부 조회하기
    public boolean checkDuplicateUsername(String username){

        return userQueryRepository
                .existsByUsername(username);
    }

    // nickname으로 중복 여부 조회하기
    public boolean checkDuplicateNickname(String nickname){

        return userQueryRepository
                .existsByNickname(nickname);
    }


    /*
     Redis에서 조회
     */
    public UserAuthCache findByUsernameInCache(String username){

        return userAuthCacheRepository
                .findByUsername(username)
                .orElseThrow(()-> new BaseException(BaseExceptionEnum
                        .USER_NOT_FOUND));

    }
}
