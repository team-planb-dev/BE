package com.planb.unit.domain.user.service;

import com.planb.domain.user.entity.AccountRecovery;
import com.planb.domain.user.entity.constant.RecoveryQuestion;
import com.planb.domain.user.dto.request.ResetPasswordRequest;
import com.planb.global.config.exception.domain.BaseException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.repository.UserRepository;
import com.planb.domain.user.service.UserService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @InjectMocks
    private UserService userService;

    @Test
    @DisplayName("유저 생성 성공")
    void create() {

        // given
        UserCreateRequest request = new UserCreateRequest(
                "testUser",
                "testNickname",
                "1234",
                RecoveryQuestion.FIRST_PET,
                "콩이",
                true,
                true,
                true
        );

        when(bCryptPasswordEncoder
                .encode("1234"))
                .thenReturn("encodedPassword");

        // when
        User user = userService.create(request);

        // then
        assertThat(user
                .getUsername())
                .isEqualTo("testUser");

        assertThat(user
                .getNickname())
                .isEqualTo("testNickname");

        assertThat(user
                .getPassword())
                .isEqualTo("encodedPassword");

        assertThat(user
                .getRole())
                .isEqualTo("USER");

        assertThat(user
                .isDeleted())
                .isFalse();

        verify(bCryptPasswordEncoder,
                times(1))
                .encode("1234");
    }

    @Test
    @DisplayName("유저 저장 성공")
    void save() {

        // given
        User user = User
                .builder()
                .username("testUser")
                .password("1234")
                .nickname("testNickname")
                .role("USER")
                .deleted(false)
                .accountRecovery(
                        AccountRecovery.of(
                                RecoveryQuestion.FIRST_PET,
                                "콩이"
                        ))
                .build();

        // when
        userService.save(user);

        // then
        verify(userRepository)
                .save(user);
    }

    @Test
    void delete() {

        // given
        User user = User
                .builder()
                .username("testUser")
                .password("1234")
                .nickname("testNickname")
                .role("USER")
                .deleted(false)
                .accountRecovery(
                        AccountRecovery.of(
                                RecoveryQuestion.FIRST_PET,
                                "콩이"
                        ))
                .build();

        // when
        userService
                .delete(user);

        // then
        assertThat(user
                .isDeleted())
                .isTrue();
    }

    @Test
    @DisplayName("복구 답변 불일치 시 비밀번호 변경 거부")
    void resetPasswordWithWrongRecoveryAnswer() {

        User user = User
                .builder()
                .username("test@example.com")
                .accountRecovery(AccountRecovery.of(RecoveryQuestion.FIRST_PET, "콩이"))
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest(
                "test@example.com",
                RecoveryQuestion.FIRST_PET,
                "틀린 답변",
                "new-password"
        );

        assertThatThrownBy(() -> userService.resetPassword(user, request))
                .isInstanceOf(BaseException.class);

        verifyNoInteractions(bCryptPasswordEncoder);
    }

    @Test
    @DisplayName("복구 답변 일치 시 비밀번호 변경")
    void resetPasswordWithMatchingRecoveryAnswer() {

        User user = User
                .builder()
                .username("test@example.com")
                .accountRecovery(AccountRecovery.of(RecoveryQuestion.FIRST_PET, "콩이"))
                .build();

        ResetPasswordRequest request = new ResetPasswordRequest(
                "test@example.com",
                RecoveryQuestion.FIRST_PET,
                "콩이",
                "new-password"
        );

        when(bCryptPasswordEncoder.encode("new-password"))
                .thenReturn("encoded-password");

        userService.resetPassword(user, request);

        assertThat(user.getPassword())
                .isEqualTo("encoded-password");
    }
}
