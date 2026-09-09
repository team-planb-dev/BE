package com.planb.domain.travel.service;

import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.travel.repository.PlanEditCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlanEditCacheService {

    private static final long EDIT_CACHE_TTL_MS = Duration.ofMinutes(25).toMillis();

    private final PlanEditCacheRepository planEditCacheRepository;

    // 4단계(수정안 생성)에서 AI 응답 저장
    public void saveEditResult(Long travelId, EditPlanAiResponse response) {

        planEditCacheRepository.save(travelId, response, EDIT_CACHE_TTL_MS);
    }

    // 5단계(저장 확정)에서 조회
    public Optional<EditPlanAiResponse> findEditResult(Long travelId) {

        return planEditCacheRepository.findByTravelId(travelId);
    }

    // 5단계(확정 완료 후)/6단계(원래대로 유지)에서 캐시 정리
    public void deleteEditResult(Long travelId) {

        planEditCacheRepository.deleteByTravelId(travelId);
    }
}
