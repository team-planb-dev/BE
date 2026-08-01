package com.planb.slice.global.security.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import com.planb.global.config.redis.RedisConfig;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.repository.UserAuthCacheRepository;

import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest(
        properties = "spring.main.allow-bean-definition-overriding=true",
        excludeAutoConfiguration = DataRedisRepositoriesAutoConfiguration.class
)
@Testcontainers
@ContextConfiguration(classes = {
        RedisConfig.class,
        UserAuthCacheRepository.class
})
class UserAuthCacheRepositoryTest {

    private static final String KEY_PREFIX = "security:auth:user:";

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4-alpine")
                    .withExposedPorts(6379);

    @Autowired
    private UserAuthCacheRepository userAuthCacheRepository;

    @Autowired
    private RedisTemplate<String, UserAuthCache> userAuthRedisTemplate;

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add(
                "spring.data.redis.port",
                () -> REDIS.getMappedPort(6379)
        );
    }

    // 테스트 후 플러싱
    @AfterEach
    void tearDown() {
        userAuthRedisTemplate
                .getConnectionFactory()
                .getConnection()
                .serverCommands()
                .flushAll();
    }

    @Test
    @DisplayName("사용자 인증 정보를 Redis에 저장 후,username으로 조회")
    void saveAndFindByUsername() {

        // given
        String username = "testUser";
        UserAuthCache userAuthCache = createUserAuthCache();

        // when
        userAuthCacheRepository.save(
                username,
                userAuthCache,
                Duration.ofMinutes(10).toMillis()
        );

        Optional<UserAuthCache> result =
                userAuthCacheRepository.findByUsername(username);

        // then
        assertThat(result).isPresent();
        assertThat(result.get()).isEqualTo(userAuthCache);
    }

    @Test
    @DisplayName("저장되지 않은 username을 조회하면 Optional.empty를 반환한다")
    void findByUsernameReturnsEmptyWhenUserDoesNotExist() {

        // given
        String username = "unknownUser";

        // when
        Optional<UserAuthCache> result =
                userAuthCacheRepository.findByUsername(username);

        // then
        assertThat(result).isEmpty();

    }

    @Test
    @DisplayName("저장한 인증 정보에 전달한 만료 시간을 설정")
    void saveSetsExpirationTime() {

        // given
        String username = "testUser";

        long expiredMs = Duration.ofMinutes(10).toMillis();
        UserAuthCache userAuthCache = createUserAuthCache();

        // when
        userAuthCacheRepository.save(
                username,
                userAuthCache,
                expiredMs
        );

        Long actualTtl = userAuthRedisTemplate.getExpire(
                KEY_PREFIX + username
        );

        // then
        assertThat(actualTtl).isNotNull();
        assertThat(actualTtl).isPositive();
        assertThat(actualTtl).isLessThanOrEqualTo(expiredMs);

    }

    @Test
    @DisplayName("만료 시간이 지나면 사용자 인증 정보가 삭제")
    void cachedUserExpires() throws InterruptedException {

        // given
        String username = "testUser";

        UserAuthCache userAuthCache = createUserAuthCache();

        userAuthCacheRepository.save(
                username,
                userAuthCache,
                100L
        );

        // when
        Thread.sleep(150L);
        Optional<UserAuthCache> result =
                userAuthCacheRepository.findByUsername(username);

        // then
        assertThat(result).isEmpty();

    }

    /*
    내부 Helper 메소드
     */

    // Redis Test 객체 생성
    private UserAuthCache createUserAuthCache() {

        return new UserAuthCache(
                1L,
                "testUser",
                "ROLE_USER"
        );
    }
}