package com.planb.ai.dto.response;

import java.util.List;

public record RebuildPlanDayResponse(
        boolean rebuilt,
        String failureReason,
        List<CreatePlanAiResponse.PlanDayDetail> planDays
) {
}
