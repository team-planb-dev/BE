package com.planb.domain.user.facade;


import com.planb.domain.user.dto.request.CheckNicknameDuplicationRequest;
import com.planb.domain.user.dto.request.CheckUsernameDuplicationRequest;
import com.planb.domain.user.dto.response.CheckNicknameDuplicationResponse;
import com.planb.domain.user.dto.response.CheckUsernameDuplicationResponse;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.domain.user.dto.response.UserCreateResponse;
import com.planb.domain.user.dto.response.UserDeleteResponse;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.service.UserService;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.service.RefreshService;
import com.planb.global.security.service.UserAuthCacheService;
import com.planb.query.user.service.UserQueryService;

import java.time.Instant;

/**
 * 사용자 계정의 생성, 조회, 삭제와 중복 검증 흐름을 조합하는 Facade.
 *
 * 사용자 저장소와 인증 캐시의 조회 경로를 Controller에 노출하지 않고
 * 계정 단위의 비즈니스 흐름으로 제공한다.
 */
@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;
    private final UserQueryService userQueryService;
    private final UserAuthCacheService userAuthCacheService;
    private final RefreshService refreshService;


    /**
     * 회원가입 요청으로 사용자를 생성하고 영속화한다.
     *
     * @param userCreateRequest 생성할 사용자 정보
     * @return 생성된 사용자 정보
     */
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


    /**
     * username으로 사용자를 확인하고 인증 세션을 제거한 뒤 계정을 soft delete 처리한다.
     *
     * @param username 삭제할 사용자의 username
     * @return 삭제 처리된 사용자 정보
     */
    @Transactional
    public UserDeleteResponse delete(String username){

        User user = userQueryService
                .findByUsername(username);

        userAuthCacheService.deleteUserAuthCache(username);

        refreshService.deleteRefreshByUsername(username);

        userService.delete(user);

        return new UserDeleteResponse(user.getUsername(),
                user.getDeletedAt());
    }

    /**
     * 인증 과정에서 재사용할 사용자 정보를 캐시에서 조회한다.
     *
     * @param username 조회할 사용자의 username
     * @return 캐시된 사용자 인증 정보
     */
    @Transactional(readOnly = true)
    public UserAuthCache findByUsername(String username){

        return userQueryService
                .findByUsernameInCache(username);
    }

    /**
     * 회원가입 전에 username 사용 가능 여부를 확인한다.
     *
     * @param checkUsernameDuplicationRequest 확인할 username
     * @return username 중복 여부
     */
    @Transactional(readOnly = true)
    public CheckUsernameDuplicationResponse checkUsernameDuplication
            (CheckUsernameDuplicationRequest checkUsernameDuplicationRequest){

        return CheckUsernameDuplicationResponse
                .result(userQueryService
                        .checkDuplicateUsername(checkUsernameDuplicationRequest
                                .username()));
    }

    /**
     * 회원가입 전에 nickname 사용 가능 여부를 확인한다.
     *
     * @param checkNicknameDuplicationRequest 확인할 nickname
     * @return nickname 중복 여부
     */
    @Transactional(readOnly = true)
    public CheckNicknameDuplicationResponse checkNicknameDuplication
            (CheckNicknameDuplicationRequest checkNicknameDuplicationRequest){

        return CheckNicknameDuplicationResponse
                .result(userQueryService
                        .checkDuplicateNickname(checkNicknameDuplicationRequest
                                .nickname()));
    }



}
