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

    public Optional<User> findById(Long id) {

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
     * 닉네임과 복구 질문·답변 해시가 일치하는 사용자 조회
     */
    public Optional<User> findByAccountRecovery(
            String nickname,
            RecoveryQuestion recoveryQuestion,
            String recoveryAnswerHash
    ) {

        return Optional.ofNullable(jpaQueryFactory
                .selectFrom(user)
                .where(
                        user
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
                                .isFalse()
                )
                .fetchOne());
    }


    // nickname 중복 체크하기
    public boolean existsByNickname(String nickname) {

        Integer result = jpaQueryFactory
                .selectOne()
                .from(user)
                .where(user.nickname.eq(nickname))
                .fetchFirst();

        return result != null;
    }

    // username(email) 중복 체크하기
    public boolean existsByUsername(String username) {

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
