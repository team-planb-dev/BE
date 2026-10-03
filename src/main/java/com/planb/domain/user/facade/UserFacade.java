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
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.domain.user.dto.response.UserCreateResponse;
import com.planb.domain.user.dto.response.UserReadResponse;
import com.planb.domain.user.dto.response.UserDeleteResponse;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.service.UserService;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.service.RefreshService;
import com.planb.global.security.service.UserAuthCacheService;
import com.planb.query.user.service.UserQueryService;

import java.util.List;

/**
 * 사용자 계정 관리 흐름을 조합
 */
@Component
@RequiredArgsConstructor
public class UserFacade {

    private final UserService userService;
    private final UserQueryService userQueryService;
    private final UserAuthCacheService userAuthCacheService;
    private final RefreshService refreshService;


    /**
     * 사용자 계정을 생성
     */
    @Transactional
    public UserCreateResponse create(UserCreateRequest userCreateRequest) {

        userQueryService.validateNotDuplicated(userCreateRequest); // 계정 중복 검증

        User user = userService.create(userCreateRequest); // 사용자 생성

        userService.save(user); // 사용자 저장

        return userService.createResponse(user); // 생성 결과 반환
    }


    /**
     * 사용자 계정 탈퇴 처리
     */
    @Transactional
    public UserDeleteResponse delete(String username) {

        User user = userQueryService.findByUsername(username); // 사용자 조회

        userAuthCacheService.deleteUserAuthCache(username); // 인증 캐시 삭제

        refreshService.deleteRefreshByUsername(username); // Refresh Token 삭제

        userService.delete(user); // 사용자 탈퇴 처리

        return userService.deleteResponse(user); // 탈퇴 결과 반환
    }

    /**
     * 사용자 정보를 조회
     */
    @Transactional(readOnly = true)
    public UserReadResponse findByUsername(String username) {

        return userQueryService.findReadResponseByUsername(username); // 사용자 정보 조회
    }

    /**
     * 계정 복구 질문 목록을 조회
     */
    public List<RecoveryQuestionResponse> findRecoveryQuestions() {

        return userService.findRecoveryQuestions(); // 복구 질문 조회
    }


    /**
     * 계정 복구 정보로 사용자 이메일을 조회
     */
    @Transactional(readOnly = true)
    public FindUsernameResponse findUsername(FindUsernameRequest findUsernameRequest) {

        return userQueryService.findUsernameResponse(findUsernameRequest); // 사용자 이메일 조회
    }


    /**
     * 계정 복구 정보로 비밀번호를 재설정
     */
    @Transactional
    public ResetPasswordResponse resetPassword(ResetPasswordRequest resetPasswordRequest) {

        User user = userQueryService.findByUsername(resetPasswordRequest.username()); // 사용자 조회

        ResetPasswordResponse response = userService.resetPassword(user, resetPasswordRequest); // 복구 정보 검증 및 비밀번호 변경

        userAuthCacheService.deleteUserAuthCache(resetPasswordRequest.username()); // 인증 캐시 삭제

        refreshService.deleteRefreshByUsername(resetPasswordRequest.username()); // Refresh Token 삭제

        return response;
    }


    /**
     * 사용자 이메일 중복 여부를 조회
     */
    @Transactional(readOnly = true)
    public CheckUsernameDuplicationResponse checkUsernameDuplication
            (CheckUsernameDuplicationRequest checkUsernameDuplicationRequest) {

        return userQueryService.checkUsernameDuplicationResponse(
                checkUsernameDuplicationRequest
        ); // 사용자 이메일 중복 조회
    }

    /**
     * 사용자 닉네임 중복 여부를 조회
     */
    @Transactional(readOnly = true)
    public CheckNicknameDuplicationResponse checkNicknameDuplication
            (CheckNicknameDuplicationRequest checkNicknameDuplicationRequest) {

        return userQueryService.checkNicknameDuplicationResponse(
                checkNicknameDuplicationRequest
        ); // 사용자 닉네임 중복 조회
    }



}
