package com.planb.query.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.domain.user.entity.AccountRecovery;
import com.planb.domain.user.dto.request.CheckNicknameDuplicationRequest;
import com.planb.domain.user.dto.request.CheckUsernameDuplicationRequest;
import com.planb.domain.user.dto.request.FindUsernameRequest;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.domain.user.dto.response.CheckNicknameDuplicationResponse;
import com.planb.domain.user.dto.response.CheckUsernameDuplicationResponse;
import com.planb.domain.user.dto.response.FindUsernameResponse;
import com.planb.domain.user.dto.response.UserReadResponse;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.entity.constant.RecoveryQuestion;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.repository.UserAuthCacheRepository;
import com.planb.query.user.repository.UserQueryRepository;

import java.util.List;

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

    public void validateNotDuplicated(UserCreateRequest request) {

        if (checkDuplicateUsername(request.username())) {
            throw new BaseException(BaseExceptionEnum.DUPLICATE_USERNAME);
        }

        if (checkDuplicateNickname(request.nickname())) {
            throw new BaseException(BaseExceptionEnum.DUPLICATE_NICKNAME);
        }
    }

    public UserReadResponse findReadResponseByUsername(String username) {

        return UserReadResponse.from(findByUsername(username));
    }

    public FindUsernameResponse findUsernameResponse(FindUsernameRequest request) {

        User user = findByAccountRecovery(
                request.nickname(),
                request.recoveryQuestion(),
                request.recoveryAnswer()
        );

        return FindUsernameResponse.of(user.getUsername());
    }

    public CheckUsernameDuplicationResponse checkUsernameDuplicationResponse(
            CheckUsernameDuplicationRequest request
    ) {

        return CheckUsernameDuplicationResponse.result(
                checkDuplicateUsername(request.username())
        );
    }

    public CheckNicknameDuplicationResponse checkNicknameDuplicationResponse(
            CheckNicknameDuplicationRequest request
    ) {

        return CheckNicknameDuplicationResponse.result(
                checkDuplicateNickname(request.nickname())
        );
    }

    /**
     * 복구 질문과 답변으로 확인한 사용자 단건 조회
     * @param nickname 계정을 특정하는 닉네임
     * @param recoveryQuestion 선택한 복구 질문
     * @param recoveryAnswer 사용자가 입력한 복구 답변
     * @return 조건에 일치하는 사용자
     */
    public User findByAccountRecovery(
            String nickname,
            RecoveryQuestion recoveryQuestion,
            String recoveryAnswer
    ) {

        // 닉네임이 없을 때와 답변이 틀렸을 때를 같은 예외로 묶는다.
        // 나누면 닉네임이 존재하는지를 알려주게 되어 계정 존재 여부가 새어 나간다.
        return userQueryRepository
                .findByAccountRecovery(
                        nickname,
                        recoveryQuestion,
                        AccountRecovery.hashAnswer(recoveryAnswer)
                )
                .orElseThrow(() -> new BaseException(BaseExceptionEnum
                        .RECOVERY_ANSWER_MISMATCH));
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

    public Long findUserIdInCache(String username) {

        return findByUsernameInCache(username).userId();
    }
}
