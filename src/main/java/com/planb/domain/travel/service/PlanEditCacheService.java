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

    // 확정 표식은 네트워크 재시도만 흡수하면 되므로 수정안보다 짧게 둔다.
    private static final long CONFIRMED_TTL_MS = Duration.ofMinutes(5).toMillis();

    private final PlanEditCacheRepository planEditCacheRepository;

    // 4단계(수정안 생성)에서 AI 응답 저장
    public void saveEditResult(Long travelId, EditPlanAiResponse response) {

        planEditCacheRepository.save(travelId, response, EDIT_CACHE_TTL_MS);
    }

    // 5단계(저장 확정)에서 조회
    public Optional<EditPlanAiResponse> findEditResult(Long travelId) {

        return planEditCacheRepository.findByTravelId(travelId);
    }

    // 5단계(저장 확정)에서 수정안을 가져오며 동시에 소비한다.
    // 같은 여행에 확정 요청이 동시에 들어와도 한 요청만 수정안을 얻는다.
    public Optional<EditPlanAiResponse> consumeEditResult(Long travelId) {

        return planEditCacheRepository.consumeByTravelId(travelId);
    }

    // 5단계(확정 완료 후) 확정 표식 기록. 같은 요청이 다시 와도 같은 응답을 돌려주기 위한 것이다.
    public void markConfirmed(Long travelId, EditPlanAiResponse response) {

        planEditCacheRepository.saveConfirmed(travelId, response, CONFIRMED_TTL_MS);
    }

    // 이미 확정된 요청의 재시도인지 판별한다.
    public Optional<EditPlanAiResponse> findConfirmedResult(Long travelId) {

        return planEditCacheRepository.findConfirmedByTravelId(travelId);
    }

    // 5단계(확정 완료 후)/6단계(원래대로 유지)에서 캐시 정리
    public void deleteEditResult(Long travelId) {

        planEditCacheRepository.deleteByTravelId(travelId);
    }
}
