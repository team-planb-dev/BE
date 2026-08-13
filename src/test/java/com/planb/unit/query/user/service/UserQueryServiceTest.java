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
        String username = "wooju@example.com";
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
        String username = "unknown@example.com";
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

    @Test
    @DisplayName("username 중복 여부 조회 시, 중복된 username이면 true 반환")
    void checkDuplicateUsername_duplicate() {

        // given
        String username = "wooju@example.com";

        when(userQueryRepository.existsByUsername(username))
                .thenReturn(true);

        // when
        boolean result = userQueryService.checkDuplicateUsername(username);

        // then
        assertThat(result).isTrue();
        verify(userQueryRepository).existsByUsername(username);
    }

    @Test
    @DisplayName("username 중복 여부 조회 시, 중복되지 않은 username이면 false 반환")
    void checkDuplicateUsername_notDuplicate() {

        // given
        String username = "wooju@example.com";

        when(userQueryRepository.existsByUsername(username))
                .thenReturn(false);

        // when
        boolean result = userQueryService.checkDuplicateUsername(username);

        // then
        assertThat(result).isFalse();
        verify(userQueryRepository).existsByUsername(username);
    }

    @Test
    @DisplayName("nickname 중복 여부 조회 시, 중복된 nickname이면 true 반환")
    void checkDuplicateNickname_duplicate() {

        // given
        String nickname = "우주";

        when(userQueryRepository.existsByNickname(nickname))
                .thenReturn(true);

        // when
        boolean result = userQueryService.checkDuplicateNickname(nickname);

        // then
        assertThat(result).isTrue();
        verify(userQueryRepository).existsByNickname(nickname);
    }

    @Test
    @DisplayName("nickname 중복 여부 조회 시, 중복되지 않은 nickname이면 false 반환")
    void checkDuplicateNickname_notDuplicate() {

        // given
        String nickname = "우주";

        when(userQueryRepository.existsByNickname(nickname))
                .thenReturn(false);

        // when
        boolean result = userQueryService.checkDuplicateNickname(nickname);

        // then
        assertThat(result).isFalse();
        verify(userQueryRepository).existsByNickname(nickname);
    }




}