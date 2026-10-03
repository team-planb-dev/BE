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

    public void save(
            Long travelId,
            EditPlanAiResponse response,
            Long expiredMs
    ) {

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

    // GETDEL의 원자적 소비를 통한 중복 확정 방지
    // Redis 6.2 이상 필요
    public Optional<EditPlanAiResponse> consumeByTravelId(Long travelId) {

        EditPlanAiResponse response = planEditRedisTemplate
                .opsForValue()
                .getAndDelete(KEY_PREFIX + travelId);

        return Optional.ofNullable(response);
    }

    public void deleteByTravelId(Long travelId) {

        planEditRedisTemplate.delete(KEY_PREFIX + travelId);
    }

    // 동일 응답 재전송을 위한 확정 완료 표식과 결과 값
    public void saveConfirmed(
            Long travelId,
            EditPlanAiResponse response,
            Long expiredMs
    ) {

        planEditRedisTemplate
                .opsForValue()
                .set(
                        CONFIRMED_KEY_PREFIX + travelId,
                        response,
                        expiredMs,
                        TimeUnit.MILLISECONDS
                );
    }

    public Optional<EditPlanAiResponse> findConfirmedByTravelId(Long travelId) {

        EditPlanAiResponse response = planEditRedisTemplate
                .opsForValue()
                .get(CONFIRMED_KEY_PREFIX + travelId);

        return Optional.ofNullable(response);
    }
}
