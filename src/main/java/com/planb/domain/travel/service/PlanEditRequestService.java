package com.planb.domain.travel.service;

import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.repository.TravelRepository;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.TravelExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * 여행별 수정 요청의 시작·취소·완료 상태 관리
 */
@Service
@RequiredArgsConstructor
public class PlanEditRequestService {

    private final TravelRepository travelRepository;
    private final TravelTransactionService travelTransactionService;
    private final PlanEditCacheService planEditCacheService;

    public String start(Long travelId) {

        return travelTransactionService.write(() -> {
            Travel travel = lock(travelId);
            String previousRequest = travel.getEditRequestId();
            String requestId = UUID.randomUUID().toString();

            travel.beginEditRequest(requestId);
            planEditCacheService.deleteRequestAfterCommit(travelId, previousRequest);

            return requestId;
        });
    }

    public void complete(
            Long travelId,
            String requestId,
            EditPlanAiResponse response
    ) {

        travelTransactionService.write(() -> {
            Travel travel = lock(travelId);

            if (!travel.isCurrentEditRequest(requestId) || travel.isEditPreviewReady()) {
                throw new BaseException(PlanEditExceptionEnum.EDIT_REQUEST_EXPIRED);
            }

            planEditCacheService.saveRequestResult(travelId, requestId, response);
            travel.markEditPreviewReady();

            return null;
        });
    }

    public void cancel(Long travelId, String expectedRequestId) {

        travelTransactionService.write(() -> {
            Travel travel = lock(travelId);

            if (expectedRequestId != null && !travel.isCurrentEditRequest(expectedRequestId)) {
                throw new BaseException(PlanEditExceptionEnum.EDIT_REQUEST_EXPIRED);
            }

            String requestId = travel.getEditRequestId();
            travel.clearEditRequest();
            planEditCacheService.deleteRequestAfterCommit(travelId, requestId);

            return null;
        });
    }

    public boolean abortIfCurrent(Long travelId, String requestId) {

        return travelTransactionService.write(() -> {
            Travel travel = lock(travelId);

            if (travel.isCurrentEditRequest(requestId)) {
                travel.clearEditRequest();
                planEditCacheService.deleteRequestAfterCommit(travelId, requestId);
                return true;
            }

            return false;
        });
    }

    public boolean isReadyCurrent(Long travelId, String requestId) {

        Travel travel = lock(travelId);

        return travel.isEditPreviewReady() && travel.isCurrentEditRequest(requestId);
    }

    public Travel lock(Long travelId) {

        return travelRepository
                .findForEditUpdate(travelId)
                .orElseThrow(() -> new BaseException(TravelExceptionEnum.TRAVEL_NOT_FOUND));
    }
}
