package com.planb.slice.global.security.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.config.redis.RedisConfig;
import com.planb.global.security.repository.UserTokenCacheRepository;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@DataRedisTest(
        properties = "spring.main.allow-bean-definition-overriding=true",
        excludeAutoConfiguration = DataRedisRepositoriesAutoConfiguration.class
)
@Testcontainers
@ContextConfiguration(classes = {
        RedisConfig.class,
        UserTokenCacheRepository.class
})
@ActiveProfiles({"test", "common-test"})
class UserTokenCacheRepositoryTest {

    @Container
    static GenericContainer<?> redisContainer =
            new GenericContainer<>(DockerImageName
                    .parse("redis:7.4-alpine"))
                    .withExposedPorts(6379);

    @DynamicPropertySource
    static void registerRedisProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.data.redis.host",
                redisContainer::getHost
        );

        registry.add(
                "spring.data.redis.port",
                redisContainer::getFirstMappedPort
        );
    }

    @Autowired
    private UserTokenCacheRepository userTokenCacheRepository;

    @Autowired
    private RedisTemplate<String, String> refreshTokenRedisTemplate;

    // 테스트 후 플러싱
    @AfterEach
    void tearDown() {
        Objects.requireNonNull(
                        refreshTokenRedisTemplate.getConnectionFactory()
        ).getConnection()
                .serverCommands()
                .flushAll();
    }

    @Test
    @DisplayName("토큰을 Redis에 저장한 후,key로 조회")
    void saveAndFindByKey() {

        // given
        String key = "refresh:user1";
        String token = "refresh-token";

        long expiredMs = 60_000L;

        // when
        userTokenCacheRepository.save(
                key,
                token,
                expiredMs
        );

        String result = userTokenCacheRepository.findByKey(key).toString();

        // then
        assertThat(result).isEqualTo(token);
    }

    @Test
    @DisplayName("저장된 토큰 키의 존재 여부 확인")
    void exists() {

        // given
        String key = "refresh:user1";

        userTokenCacheRepository.save(
                key,
                "refresh-token",
                60_000L
        );

        // when
        boolean result = userTokenCacheRepository.exists(key);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("저장된 토큰삭제")
    void delete() {

        // given
        String key = "refresh:user1";

        userTokenCacheRepository.save(
                key,
                "refresh-token",
                60_000L
        );

        // when
        userTokenCacheRepository.delete(key);

        // then
        assertThat(userTokenCacheRepository
                .exists(key))
                .isFalse();
    }

    @Test
    @DisplayName("토큰 저장 시,TTL 적용")
    void saveWithExpiration() {

        // given
        String key = "refresh:user1";

        long expiredMs = 60_000L;

        // when
        userTokenCacheRepository
                .save(
                        key,
                        "refresh-token",
                        expiredMs
        );

        Long ttl = refreshTokenRedisTemplate
                .getExpire(
                        key,
                        TimeUnit.MILLISECONDS
        );

        // then
        assertThat(ttl)
                .isNotNull()
                .isPositive()
                .isLessThanOrEqualTo(expiredMs);
    }

    @Test
    @DisplayName("존재하지 않는 토큰을 조회 시,예외 발생")
    void findByKeyNotFound() {

        // given
        String key = "unknown";

        // when & then
        assertThatThrownBy(
                () -> userTokenCacheRepository.findByKey(key)
        ).isInstanceOf(BaseException.class);
    }


}