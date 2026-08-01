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
@ActiveProfiles("test")
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
                .username("testUser")
                .password("1234")
                .deleted(false)
                .role("ROLE_USER")
                .nickname("testUser")
                .build();

        userRepository.save(user);

        // when
        Optional<User> result = userQueryRepository
                .findByUsername("testUser");

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
                .findByUsername("unknown");

        // then
        assertThat(result)
                .isEmpty();
    }



}