package com.planb.domain.travel.service;

import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.travel.repository.PlanEditCacheRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.planb.domain.travel.repository.TravelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class PlanEditCacheService {

    private static final long EDIT_CACHE_TTL_MS = Duration
            .ofMinutes(25)
            .toMillis();

    // 네트워크 재시도만 처리하는 단기 확정 표식
    private static final long CONFIRMED_TTL_MS = Duration
            .ofMinutes(5)
            .toMillis();

    private final PlanEditCacheRepository planEditCacheRepository;
    private final TravelRepository travelRepository;

    public void saveRequestResult(
            Long travelId,
            String requestId,
            EditPlanAiResponse response
    ) {

        planEditCacheRepository.saveRequest(
                travelId,
                requestId,
                response,
                EDIT_CACHE_TTL_MS
        );
    }

    public Optional<EditPlanAiResponse> findRequestResult(Long travelId, String requestId) {

        return planEditCacheRepository.findRequest(travelId, requestId);
    }

    public Optional<EditPlanAiResponse> findConfirmedRequest(Long travelId, String requestId) {

        try {
            return planEditCacheRepository.findConfirmedRequest(travelId, requestId);
        } catch (RuntimeException failure) {
            log.warn("[PLAN EDIT CACHE] confirmed result unavailable, travelId={}", travelId);
            return Optional.empty();
        }
    }

    public void deleteRequestAfterCommit(Long travelId, String requestId) {

        if (requestId == null) {
            return;
        }

        afterCommit(() -> planEditCacheRepository.deleteRequest(travelId, requestId));
    }

    public void confirmRequestAfterCommit(
            Long travelId,
            String requestId,
            EditPlanAiResponse response
    ) {

        afterCommit(() -> {
            planEditCacheRepository.saveConfirmedRequest(
                    travelId,
                    requestId,
                    response,
                    CONFIRMED_TTL_MS
            );
            planEditCacheRepository.deleteRequest(travelId, requestId);
        });
    }

    private void afterCommit(Runnable operation) {

        Runnable safeOperation = () -> {
            try {
                operation.run();
            } catch (RuntimeException failure) {
                // SQL 확정 상태를 Redis 정리 실패로 되돌리지 않는 계약
                log.warn("[PLAN EDIT CACHE] post-commit cleanup failed");
            }
        };

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            safeOperation.run();
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                safeOperation.run();
            }
        });
    }


    // 5단계(저장 확정)에서 조회
    public Optional<EditPlanAiResponse> findEditResult(Long travelId) {

        return travelRepository
                .findById(travelId)
                .filter(travel -> travel.isEditPreviewReady() && travel.getEditRequestId() != null)
                .flatMap(travel -> findRequestResult(travelId, travel.getEditRequestId()));
    }

}
