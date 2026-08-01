package com.planb.domain.user.facade;


import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.domain.user.dto.response.UserCreateResponse;
import com.planb.domain.user.dto.response.UserDeleteResponse;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.service.UserService;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.query.user.service.UserQueryService;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;
    private final UserQueryService userQueryService;


    @Transactional
    public UserCreateResponse create(UserCreateRequest userCreateRequest){

        // 유저 생성
        User user = userService.create(userCreateRequest);

        // DB에 저장
        userService.save(user);

        return new UserCreateResponse(user.getUsername(),
                Instant.now(),
                Instant.now());
    }


    @Transactional
    public UserDeleteResponse delete(String username){

        User user = userQueryService
                .findByUsername(username);

        // DB 정보 삭제
        userService.delete(user);

        // Redis 토큰 삭제 ( 추가 필요 )

        return new UserDeleteResponse(user.getUsername(),
                user.getDeletedAt());
    }

    @Transactional(readOnly = true)
    public UserAuthCache findByUsername(String username){

        return userQueryService
                .findByUsernameInCache(username);
    }



}
