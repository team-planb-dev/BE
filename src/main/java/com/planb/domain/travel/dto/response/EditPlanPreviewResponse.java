package com.planb.domain.travel.dto.response;

import com.planb.ai.dto.response.EditPlanAiResponse;

public record EditPlanPreviewResponse(
        GetAiPlanResponse before,
        EditPlanAiResponse after
) {
}
