package com.planb.domain.travel.service;

import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.travel.repository.PlanEditCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PlanEditCacheService {

    private static final long EDIT_CACHE_TTL_MS = Duration
            .ofMinutes(25)
            .toMillis();

    // 네트워크 재시도만 처리하는 단기 확정 표식
    private static final long CONFIRMED_TTL_MS = Duration
            .ofMinutes(5)
            .toMillis();

    private final PlanEditCacheRepository planEditCacheRepository;

    // 4단계(수정안 생성)에서 AI 응답 저장
    public void saveEditResult(Long travelId, EditPlanAiResponse response) {

        planEditCacheRepository.save(
                travelId,
                response,
                EDIT_CACHE_TTL_MS
        );
    }

    // 5단계(저장 확정)에서 조회
    public Optional<EditPlanAiResponse> findEditResult(Long travelId) {

        return planEditCacheRepository.findByTravelId(travelId);
    }

    // 5단계 저장 확정 시 수정안의 조회·소비
    // 동일 여행의 동시 확정 요청 중 단일 수정안 소비
    public Optional<EditPlanAiResponse> consumeEditResult(Long travelId) {

        return planEditCacheRepository.consumeByTravelId(travelId);
    }

    // 5단계 확정 완료 후 동일 응답 재전송용 표식 기록
    public void markConfirmed(Long travelId, EditPlanAiResponse response) {

        planEditCacheRepository.saveConfirmed(
                travelId,
                response,
                CONFIRMED_TTL_MS
        );
    }

    public void markConfirmedAfterCommit(
            Long travelId,
            EditPlanAiResponse response
    ) {

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            markConfirmed(travelId, response);
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {
                        markConfirmed(travelId, response);
                    }
                }
        );
    }

    // 기존 확정 요청의 재시도 판별
    public Optional<EditPlanAiResponse> findConfirmedResult(Long travelId) {

        return planEditCacheRepository.findConfirmedByTravelId(travelId);
    }

    // 5단계(확정 완료 후)/6단계(원래대로 유지)에서 캐시 정리
    public void deleteEditResult(Long travelId) {

        planEditCacheRepository.deleteByTravelId(travelId);
    }
}
