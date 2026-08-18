package com.planb.slice.query.user.repository;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import com.planb.domain.user.entity.User;
import com.planb.domain.user.repository.UserRepository;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.user.repository.UserQueryRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({QueryDslConfig.class, UserQueryRepository.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("local")
class UserQueryRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserQueryRepository userQueryRepository;

    @Test
    @DisplayName("username으로 사용자 조회 성공")
    void findByUsername_success(){

        // given
        User user = User.builder()
                .username("testUser@example.com")
                .password("1234")
                .deleted(false)
                .role("ROLE_USER")
                .nickname("testUser")
                .build();

        userRepository.save(user);

        // when
        Optional<User> result = userQueryRepository
                .findByUsername("testUser@example.com");

        // then
        assertThat(result
                .isPresent())
                .isTrue();

        assertThat(result
                .get()
                .getUsername())
                .isEqualTo(user
                        .getUsername());

        assertThat(result
                .get()
                .getNickname())
                .isEqualTo(user
                        .getNickname());

    }

    @Test
    @DisplayName("존재하지 않는 username 조회 시 , Optional.empty 반환")
    void findByUsername_failure(){

        // when
        Optional<User> result = userQueryRepository
                .findByUsername("unknown@example.com");

        // then
        assertThat(result)
                .isEmpty();
    }

    @Test
    @DisplayName("존재하는 username 중복 체크 시, true 반환")
    void existsByUsername_true() {

        // given
        User user = User.builder()
                .username("test@example.com")
                .password("1234")
                .deleted(false)
                .role("ROLE_USER")
                .nickname("testNickname")
                .build();

        userRepository.save(user);

        // when
        boolean result = userQueryRepository
                .existsByUsername("test@example.com");

        // then
        assertThat(result)
                .isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 username 중복 체크 시, false 반환")
    void existsByUsername_false() {

        // when
        boolean result = userQueryRepository
                .existsByUsername("unknown@example.com");

        // then
        assertThat(result)
                .isFalse();
    }

    @Test
    @DisplayName("존재하는 nickname 중복 체크 시, true 반환")
    void existsByNickname_true() {

        // given
        User user = User.builder()
                .username("nickname-test@example.com")
                .password("test1234!")
                .deleted(false)
                .role("ROLE_USER")
                .nickname("testNickname")
                .build();

        userRepository.save(user);

        // when
        boolean result = userQueryRepository
                .existsByNickname("testNickname");

        // then
        assertThat(result)
                .isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 nickname 중복 체크 시, false 반환")
    void existsByNickname_false() {

        // when
        boolean result = userQueryRepository
                .existsByNickname("unknownNickname");

        // then
        assertThat(result)
                .isFalse();
    }
}