package com.planb.unit.query.user.service;

import com.planb.domain.user.entity.AccountRecovery;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.entity.constant.RecoveryQuestion;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.repository.UserAuthCacheRepository;
import com.planb.query.user.repository.UserQueryRepository;
import com.planb.query.user.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceRecoveryTest {

    @Mock
    private UserQueryRepository userQueryRepository;

    @Mock
    private UserAuthCacheRepository userAuthCacheRepository;

    @InjectMocks
    private UserQueryService userQueryService;

    @Test
    @DisplayName("닉네임과 복구 답변이 일치하는 유저 반환")
    void findsMatchedUser() {

        // given
        User user = User
                .builder()
                .username("yeonwoo@gmail.com")
                .nickname("우주")
                .accountRecovery(AccountRecovery
                        .of(RecoveryQuestion.FIRST_PET, "콩이"))
                .build();

        when(userQueryRepository
                .findByAccountRecovery(anyString(), any(), anyString()))
                .thenReturn(Optional.of(user));

        // when
        User found = userQueryService
                .findByAccountRecovery("우주", RecoveryQuestion.FIRST_PET, "콩이");

        // then
        assertThat(found
                .getUsername())
                .isEqualTo("yeonwoo@gmail.com");
    }

    @Test
    @DisplayName("닉네임으로 계정을 특정하므로 조회 조건에 닉네임을 그대로 전달")
    void passesNicknameToRepository() {

        // given
        when(userQueryRepository
                .findByAccountRecovery(anyString(), any(), anyString()))
                .thenReturn(Optional.of(User
                        .builder()
                        .username("yeonwoo@gmail.com")
                        .nickname("우주")
                        .build()));

        // when
        userQueryService
                .findByAccountRecovery("우주", RecoveryQuestion.FIRST_PET, "콩이");

        // then
        verify(userQueryRepository)
                .findByAccountRecovery(
                        eq("우주"),
                        eq(RecoveryQuestion.FIRST_PET),
                        eq(AccountRecovery.hashAnswer("콩이")));
    }

    @Test
    @DisplayName("일치하는 계정이 없는 경우 실패 처리")
    void failsWhenNoUserMatches() {

        // given
        when(userQueryRepository
                .findByAccountRecovery(anyString(), any(), anyString()))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userQueryService
                .findByAccountRecovery("우주", RecoveryQuestion.FIRST_PET, "콩이"))
                .isInstanceOf(BaseException.class);
    }
}
