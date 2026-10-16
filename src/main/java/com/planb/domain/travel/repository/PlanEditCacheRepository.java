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

    private static final String CONFIRMED_KEY_PREFIX = "plan-edit-confirmed:";

    private final RedisTemplate<String, EditPlanAiResponse> planEditRedisTemplate;

    public void saveRequest(
            Long travelId,
            String requestId,
            EditPlanAiResponse response,
            long expiredMs
    ) {

        planEditRedisTemplate
                .opsForValue()
                .set(
                        requestKey(travelId, requestId),
                        response,
                        expiredMs,
                        TimeUnit.MILLISECONDS
                );
    }

    public Optional<EditPlanAiResponse> findRequest(Long travelId, String requestId) {

        return Optional.ofNullable(planEditRedisTemplate
                .opsForValue()
                .get(requestKey(travelId, requestId)));
    }

    public void deleteRequest(Long travelId, String requestId) {

        planEditRedisTemplate.delete(requestKey(travelId, requestId));
    }

    public void saveConfirmedRequest(
            Long travelId,
            String requestId,
            EditPlanAiResponse response,
            long expiredMs
    ) {

        planEditRedisTemplate
                .opsForValue()
                .set(
                        confirmedKey(travelId, requestId),
                        response,
                        expiredMs,
                        TimeUnit.MILLISECONDS
                );
    }

    public Optional<EditPlanAiResponse> findConfirmedRequest(Long travelId, String requestId) {

        return Optional.ofNullable(planEditRedisTemplate
                .opsForValue()
                .get(confirmedKey(travelId, requestId)));
    }

    private String requestKey(Long travelId, String requestId) {

        return KEY_PREFIX + travelId + ":" + requestId;
    }

    private String confirmedKey(Long travelId, String requestId) {

        return CONFIRMED_KEY_PREFIX + travelId + ":" + requestId;
    }

}
