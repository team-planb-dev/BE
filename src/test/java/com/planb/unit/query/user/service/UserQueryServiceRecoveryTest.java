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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
    @DisplayName("복구 질문과 답변이 유일하게 일치하는 유저 반환")
    void findsSingleMatchedUser() {

        // given
        User user = User
                .builder()
                .username("yeonwoo@gmail.com")
                .accountRecovery(AccountRecovery
                        .of(RecoveryQuestion.FIRST_PET, "콩이"))
                .build();

        when(userQueryRepository
                .findAllByAccountRecovery(any(), anyString()))
                .thenReturn(List.of(user));

        // when
        User found = userQueryService
                .findByAccountRecovery(RecoveryQuestion.FIRST_PET, "콩이");

        // then
        assertThat(found
                .getUsername())
                .isEqualTo("yeonwoo@gmail.com");
    }

    @Test
    @DisplayName("복구 답변이 여러 계정에 일치하는 경우 실패 처리")
    void failsWhenMultipleUsersMatch() {

        // given
        when(userQueryRepository
                .findAllByAccountRecovery(any(), anyString()))
                .thenReturn(List.of(
                        User.builder().username("a@gmail.com").accountRecovery(
                                AccountRecovery.of(
                                        RecoveryQuestion.FIRST_PET,
                                        "콩이"
                                ))
                        .build(),
                        User.builder().username("b@gmail.com").accountRecovery(
                                AccountRecovery.of(
                                        RecoveryQuestion.FIRST_PET,
                                        "콩이"
                                ))
                        .build()
                ));

        // when & then
        assertThatThrownBy(() -> userQueryService
                .findByAccountRecovery(RecoveryQuestion.FIRST_PET, "콩이"))
                .isInstanceOf(BaseException.class);
    }

    @Test
    @DisplayName("복구 답변이 일치하는 계정이 없는 경우 실패 처리")
    void failsWhenNoUserMatches() {

        // given
        when(userQueryRepository
                .findAllByAccountRecovery(any(), anyString()))
                .thenReturn(List.of());

        // when & then
        assertThatThrownBy(() -> userQueryService
                .findByAccountRecovery(RecoveryQuestion.FIRST_PET, "콩이"))
                .isInstanceOf(BaseException.class);
    }
}
