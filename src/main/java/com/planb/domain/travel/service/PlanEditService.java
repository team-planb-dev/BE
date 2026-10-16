package com.planb.domain.travel.service;

import com.planb.ai.context.PlanEditContext;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.entity.Travel;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * 수정 요청 수명과 AI 미리보기 생성의 공통 흐름
 */
@Service
@RequiredArgsConstructor
public class PlanEditService {

    private final PlanEditRequestService planEditRequestService;
    private final TravelService travelService;
    private final TravelTransactionService travelTransactionService;
    private final PlanService planService;
    private final PlanEditCacheService planEditCacheService;

    public EditPlanPreviewResponse makePreview(Long travelId, String message) {

        String requestId = planEditRequestService.start(travelId);

        try {
            PlanEditContext context = travelService.prepareEditContext(travelId, message);
            EditPlanAiResponse response = planService.makeEditPlanByAi(context);
            planEditRequestService.complete(travelId, requestId, response);

            return new EditPlanPreviewResponse(
                    context.currentPlan(),
                    response,
                    requestId
            );
        } catch (RuntimeException failure) {
            try {
                if (!planEditRequestService.abortIfCurrent(travelId, requestId)) {
                    BaseException expired = new BaseException(PlanEditExceptionEnum.EDIT_REQUEST_EXPIRED);
                    expired.addSuppressed(failure);
                    throw expired;
                }
            } catch (BaseException expired) {
                if (PlanEditExceptionEnum.EDIT_REQUEST_EXPIRED.getCode().equals(expired.getErrorCode())) {
                    throw expired;
                }
                failure.addSuppressed(expired);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
            }

            throw failure;
        }
    }

    public Optional<EditPlanPreviewResponse> makeChatPreview(Long travelId, String message) {

        try {
            return Optional.of(makePreview(travelId, message));
        } catch (BaseException failure) {
            if (PlanEditExceptionEnum.EDIT_REQUEST_EXPIRED.getCode().equals(failure.getErrorCode())) {
                return Optional.empty();
            }

            throw failure;
        }
    }

    public EditPlanPreviewResponse findPreview(Long travelId) {

        return travelTransactionService.write(() -> {
            Travel travel = planEditRequestService.lock(travelId);
            String requestId = travel.getEditRequestId();

            if (requestId == null) {
                throw new BaseException(PlanEditExceptionEnum.EDIT_RESULT_NOT_FOUND);
            }

            if (!travel.isEditPreviewReady()) {
                throw new BaseException(PlanEditExceptionEnum.EDIT_RESULT_NOT_READY);
            }

            EditPlanAiResponse response = planEditCacheService
                    .findRequestResult(travelId, requestId)
                    .orElseThrow(() -> new BaseException(PlanEditExceptionEnum.EDIT_RESULT_NOT_FOUND));

            return new EditPlanPreviewResponse(
                    travelService.loadCurrentPlan(travelId),
                    response,
                    requestId
            );
        });
    }
}
