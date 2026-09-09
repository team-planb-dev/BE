package com.planb.domain.user.facade;


import com.planb.domain.user.dto.request.CheckNicknameDuplicationRequest;
import com.planb.domain.user.dto.request.CheckUsernameDuplicationRequest;
import com.planb.domain.user.dto.request.FindUsernameRequest;
import com.planb.domain.user.dto.request.ResetPasswordRequest;
import com.planb.domain.user.dto.response.CheckNicknameDuplicationResponse;
import com.planb.domain.user.dto.response.CheckUsernameDuplicationResponse;
import com.planb.domain.user.dto.response.FindUsernameResponse;
import com.planb.domain.user.dto.response.RecoveryQuestionResponse;
import com.planb.domain.user.dto.response.ResetPasswordResponse;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
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
import java.util.List;

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
     * 회원가입과 계정 복구 화면에서 선택할 수 있는 복구 질문 목록을 반환한다.
     *
     * @return 복구 질문 코드와 문구 목록
     */
    public List<RecoveryQuestionResponse> findRecoveryQuestions(){

        return RecoveryQuestionResponse
                .all();
    }


    /**
     * 계정 복구 질문과 답변으로 가입된 이메일을 찾는다.
     *
     * @param findUsernameRequest 복구 질문과 답변
     * @return 마스킹된 이메일
     */
    @Transactional(readOnly = true)
    public FindUsernameResponse findUsername(FindUsernameRequest findUsernameRequest){

        User user = userQueryService
                .findByAccountRecovery(
                        findUsernameRequest
                                .recoveryQuestion(),
                        findUsernameRequest
                                .recoveryAnswer()
                );

        return FindUsernameResponse
                .of(user.getUsername());
    }


    /**
     * 이메일과 계정 복구 질문/답변을 확인한 뒤 비밀번호를 재설정한다.
     *
     * 비밀번호가 바뀌면 기존 토큰과 인증 캐시는 더 이상 유효하지 않아야 하므로 함께 제거한다.
     *
     * @param resetPasswordRequest 이메일, 복구 질문, 복구 답변, 새 비밀번호
     * @return 재설정된 계정 정보
     */
    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest resetPasswordRequest){

        User user = userQueryService
                .findByUsername(resetPasswordRequest
                        .username());

        boolean matched = user
                .getAccountRecovery() != null
                && user
                        .getAccountRecovery()
                        .matches(
                                resetPasswordRequest
                                        .recoveryQuestion(),
                                resetPasswordRequest
                                        .recoveryAnswer()
                        );

        if (!matched) {
            throw new BaseException(BaseExceptionEnum
                    .RECOVERY_ANSWER_MISMATCH);
        }

        userService.resetPassword(user,
                resetPasswordRequest
                        .newPassword());

        userAuthCacheService.deleteUserAuthCache(user
                .getUsername());

        refreshService.deleteRefreshByUsername(user
                .getUsername());

        return new ResetPasswordResponse(user.getUsername(),
                Instant.now());
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
