package com.planb.unit.query.user.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.planb.domain.user.entity.User;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.query.user.repository.UserQueryRepository;
import com.planb.query.user.service.UserQueryService;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceTest {

    @Mock
    private UserQueryRepository userQueryRepository;

    @InjectMocks
    private UserQueryService userQueryService;


    @Test
    @DisplayName("username으로 user 조회 시, 성공")
    public void findByUsername_success() {

        // given
        String username = "wooju";
        User user = mock(User.class);

        when(userQueryRepository
                .findByUsername(username))
                .thenReturn(Optional.of(user));

        // when
        User result = userQueryService.findByUsername(username);

        // then
        assertThat(result).isSameAs(user);
        verify(userQueryRepository).findByUsername(username);

    }

    @Test
    @DisplayName("username에 해당하는 user가 없을 시, USER_NOT_FOUND 예외 발생")
    void findByUsername_fail_userNotFound() {

        // given
        String username = "unknown";
        when(userQueryRepository.findByUsername(username))
                .thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> userQueryService.findByUsername(username))
                .isInstanceOf(BaseException.class)
                .satisfies(exception -> {

                    BaseException baseException = (BaseException) exception;
                    assertThat(baseException.getMessage())
                            .isEqualTo(BaseExceptionEnum.USER_NOT_FOUND.getMessage());

                });

        verify(userQueryRepository).findByUsername(username);

    }


}