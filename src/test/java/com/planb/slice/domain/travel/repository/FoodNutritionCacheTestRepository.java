package com.planb.slice.domain.travel.repository;

import com.planb.domain.travel.repository.FoodNutritionCacheRepository;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.config.redis.RedisConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.redis.autoconfigure.DataRedisRepositoriesAutoConfiguration;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataRedisTest(
        properties = "spring.main.allow-bean-definition-overriding=true",
        excludeAutoConfiguration = DataRedisRepositoriesAutoConfiguration.class
)
@Testcontainers
@ContextConfiguration(classes = {
        RedisConfig.class,
        FoodNutritionCacheRepository.class
})
class FoodNutritionCacheTestRepository {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7.4-alpine")
                    .withExposedPorts(6379);

    @Autowired
    private FoodNutritionCacheRepository foodNutritionCacheRepository;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @DynamicPropertySource
    static void configureRedis(DynamicPropertyRegistry registry) {

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add(
                "spring.data.redis.port",
                () -> REDIS.getMappedPort(6379)
        );

        // 프로필의 spring.data.redis.url이 host·port보다 우선하므로 컨테이너 주소로 덮어씀
        registry.add(
                "spring.data.redis.url",
                () -> "redis://" + REDIS.getHost() + ":" + REDIS.getMappedPort(6379)
        );
    }

    // 테스트 후 플러싱
    @AfterEach
    void tearDown() {

        redisConnectionFactory
                .getConnection()
                .serverCommands()
                .flushAll();
    }

    @Test
    @DisplayName("식약처 응답 필드명 그대로 저장 후 같은 값으로 조회")
    void savesAndFindsItemsByName() {

        FoodNtrCpntResponse.Item item = item("비빔밥");

        foodNutritionCacheRepository.save(
                "비빔밥",
                List.of(item),
                Duration.ofDays(30)
        );

        assertThat(foodNutritionCacheRepository.find("비빔밥"))
                .contains(List.of(item));
    }

    @Test
    @DisplayName("빈 조회 결과도 저장해 미적중과 구분")
    void distinguishesCachedEmptyResultFromMiss() {

        foodNutritionCacheRepository.save(
                "없는메뉴",
                List.of(),
                Duration.ofDays(1)
        );

        assertThat(foodNutritionCacheRepository.find("없는메뉴"))
                .contains(List.of());

        assertThat(foodNutritionCacheRepository.find("저장안한메뉴"))
                .isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("저장 시 전달한 보존 기간을 Redis 만료 시간으로 설정")
    void setsTtl() {

        foodNutritionCacheRepository.save(
                "비빔밥",
                List.of(item("비빔밥")),
                Duration.ofDays(30)
        );

        Long ttlSeconds = new StringRedisTemplate(redisConnectionFactory).getExpire("nutrition:food:비빔밥");

        assertThat(ttlSeconds)
                .isPositive()
                .isLessThanOrEqualTo(Duration
                        .ofDays(30)
                        .toSeconds());
    }

    private FoodNtrCpntResponse.Item item(String foodName) {

        return new FoodNtrCpntResponse.Item(
                "FOOD001",
                foodName,
                "음식DB",
                "",
                "",
                "100g",
                "100.0",
                "5.0",
                "3.0",
                "50.0",
                "8.0",
                "6.0",
                "600.0",
                "70.0",
                "3.0",
                "0.5"
        );
    }
}
