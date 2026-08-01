package com.planb.unit.domain.user.service;

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
                "1234"
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
                .build();

        // when
        userService.save(user);

        // then
        verify(userRepository).save(user);
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
                .build();

        // when
        userService
                .delete(user);

        // then
        assertThat(user
                .isDeleted())
                .isTrue();
    }
}