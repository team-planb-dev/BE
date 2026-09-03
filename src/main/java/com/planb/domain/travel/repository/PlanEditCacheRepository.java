package com.planb.domain.travel.repository;

import com.planb.ai.dto.response.EditPlanAiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class PlanEditCacheRepository {

    private static final String KEY_PREFIX = "plan-edit:";

    private final RedisTemplate<String, EditPlanAiResponse> planEditRedisTemplate;

    public void save(Long travelId, EditPlanAiResponse response, Long expiredMs) {

        planEditRedisTemplate
                .opsForValue()
                .set(
                        KEY_PREFIX + travelId,
                        response,
                        expiredMs,
                        TimeUnit.MILLISECONDS
                );
    }

    public Optional<EditPlanAiResponse> findByTravelId(Long travelId) {

        EditPlanAiResponse response = planEditRedisTemplate
                .opsForValue()
                .get(KEY_PREFIX + travelId);

        return Optional.ofNullable(response);
    }

    public void deleteByTravelId(Long travelId) {

        planEditRedisTemplate.delete(KEY_PREFIX + travelId);
    }
}
