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

    // GETDEL로 조회와 삭제를 한 번에 끝내 동시 확정 요청 중 하나만 수정안을 가져가게 한다.
    // Redis 6.2 이상 필요.
    public Optional<EditPlanAiResponse> consumeByTravelId(Long travelId) {

        EditPlanAiResponse response = planEditRedisTemplate
                .opsForValue()
                .getAndDelete(KEY_PREFIX + travelId);

        return Optional.ofNullable(response);
    }

    public void deleteByTravelId(Long travelId) {

        planEditRedisTemplate.delete(KEY_PREFIX + travelId);
    }

    // 확정 완료 표식. 값까지 함께 남겨 재확정 요청에 같은 응답을 돌려줄 수 있게 한다.
    public void saveConfirmed(Long travelId, EditPlanAiResponse response, Long expiredMs) {

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
