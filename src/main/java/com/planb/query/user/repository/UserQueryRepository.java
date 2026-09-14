package com.planb.query.user.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import com.planb.domain.user.entity.QUser;
import com.planb.domain.user.entity.User;

import com.planb.domain.user.entity.constant.RecoveryQuestion;

import java.util.List;
import java.util.Optional;


@Repository
@RequiredArgsConstructor
public class UserQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final QUser user = QUser.user;

    public Optional<User> findByUsername(String username) {

        return Optional.ofNullable(
                jpaQueryFactory
                        .selectFrom(user)
                        .where(user.username.eq(username))
                        .fetchOne()

        );

    }

    public Optional<User> findById(Long id){

        return Optional.ofNullable(
                jpaQueryFactory
                        .selectFrom(user)
                        .where(user
                                .id
                                .eq(id))
                        .fetchOne()
        );
    }


    /**
     * 닉네임과 계정 복구 질문/답변 해시가 모두 일치하는 사용자를 조회한다.
     *
     * 닉네임은 uk_users_nickname으로 유일하므로 결과는 최대 한 건이다.
     *
     * @param nickname 계정을 특정하는 닉네임
     * @param recoveryQuestion   선택한 복구 질문
     * @param recoveryAnswerHash 정규화 후 해시한 복구 답변
     * @return 조건에 일치하는 사용자 목록
     */
    public Optional<User> findByAccountRecovery(
            String nickname,
            RecoveryQuestion recoveryQuestion,
            String recoveryAnswerHash
    ) {

        return Optional.ofNullable(jpaQueryFactory
                .selectFrom(user)
                .where(user
                                .nickname
                                .eq(nickname),
                        user
                                .accountRecovery
                                .recoveryQuestion
                                .eq(recoveryQuestion),
                        user
                                .accountRecovery
                                .recoveryAnswerHash
                                .eq(recoveryAnswerHash),
                        user
                                .deleted
                                .isFalse())
                .fetchOne());
    }


    // nickname 중복 체크하기
    public boolean existsByNickname(String nickname){

        Integer result = jpaQueryFactory
                .selectOne()
                .from(user)
                .where(user.nickname.eq(nickname))
                .fetchFirst();

        return result != null;
    }

    // username(email) 중복 체크하기
    public boolean existsByUsername(String username){

        Integer result = jpaQueryFactory
                .selectOne()
                .from(user)
                .where(user
                        .username
                        .eq(username))
                .fetchFirst();

        return result != null;
    }

}
