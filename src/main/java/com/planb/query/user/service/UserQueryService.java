package com.planb.query.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.domain.user.entity.AccountRecovery;
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

    /**
     * 계정 복구 질문과 답변으로 사용자를 단건 조회한다.
     *
     * 닉네임으로 계정을 특정하고 질문/답변으로 본인을 확인한다.
     *
     * @param nickname         계정을 특정하는 닉네임
     * @param recoveryQuestion 선택한 복구 질문
     * @param recoveryAnswer   사용자가 입력한 복구 답변
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
}
