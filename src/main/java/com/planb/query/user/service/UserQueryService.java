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
     * 일치하는 사용자가 없을 때와 여러 명일 때를 모두 실패로 처리한다.
     * 흔한 답변이 여러 계정에 걸릴 수 있으므로 임의의 계정을 돌려주면 다른 사람의 이메일이 노출된다.
     *
     * @param recoveryQuestion 선택한 복구 질문
     * @param recoveryAnswer   사용자가 입력한 복구 답변
     * @return 조건에 유일하게 일치하는 사용자
     */
    public User findByAccountRecovery(
            RecoveryQuestion recoveryQuestion,
            String recoveryAnswer
    ) {

        List<User> users = userQueryRepository
                .findAllByAccountRecovery(
                        recoveryQuestion,
                        AccountRecovery.hashAnswer(recoveryAnswer)
                );

        if (users.size() != 1) {
            throw new BaseException(BaseExceptionEnum
                    .RECOVERY_ANSWER_MISMATCH);
        }

        return users
                .getFirst();
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
