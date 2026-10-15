package com.planb.domain.travel.dto.response;

import com.planb.ai.dto.response.EditPlanAiResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record EditPlanPreviewResponse(
        @Schema(description = "수정 전 확정 일정")
        GetAiPlanResponse before,
        @Schema(description = "서버 검증을 거친 AI 수정 미리보기")
        EditPlanAiResponse after,
        @Schema(description = "서버 수정 요청 식별값, 재연결·확정·취소에 사용")
        String editRequestId
) {

    public EditPlanPreviewResponse(GetAiPlanResponse before, EditPlanAiResponse after) {
        this(before, after, null);
    }

}
