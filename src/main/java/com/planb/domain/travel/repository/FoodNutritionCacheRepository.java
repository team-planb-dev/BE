package com.planb.domain.travel.repository;

import com.planb.domain.travel.dto.nutrition.FoodNutritionCache;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 식약처 영양 조회 원본 결과 캐시
 *
 * 질환별 평가가 아닌 조회 결과만 저장해 여행자 조건과 무관하게 재사용
 */
@Repository
@RequiredArgsConstructor
public class FoodNutritionCacheRepository {

    private static final String KEY_PREFIX = "nutrition:food:";

    private final RedisTemplate<String, FoodNutritionCache> foodNutritionRedisTemplate;

    public void save(
            String name,
            List<FoodNtrCpntResponse.Item> items,
            Duration ttl
    ) {

        foodNutritionRedisTemplate
                .opsForValue()
                .set(
                        KEY_PREFIX + name,
                        new FoodNutritionCache(List.copyOf(items)),
                        ttl
                );
    }

    public Optional<List<FoodNtrCpntResponse.Item>> find(String name) {

        FoodNutritionCache cache = foodNutritionRedisTemplate
                .opsForValue()
                .get(KEY_PREFIX + name);

        return cache == null || cache.items() == null
                ? Optional.empty()
                : Optional.of(cache.items());
    }
}
