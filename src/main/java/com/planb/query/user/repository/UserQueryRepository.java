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
     * 계정 복구 질문과 답변 해시가 모두 일치하는 사용자를 조회한다.
     *
     * 이메일 찾기는 username 없이 질문/답변만으로 검색하므로 결과가 여러 건일 수 있다.
     * 임의의 계정을 돌려주지 않도록 목록 그대로 반환하고 판단은 상위 계층에 맡긴다.
     *
     * @param recoveryQuestion   선택한 복구 질문
     * @param recoveryAnswerHash 정규화 후 해시한 복구 답변
     * @return 조건에 일치하는 사용자 목록
     */
    public List<User> findAllByAccountRecovery(
            RecoveryQuestion recoveryQuestion,
            String recoveryAnswerHash
    ) {

        return jpaQueryFactory
                .selectFrom(user)
                .where(user
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
                .fetch();
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
