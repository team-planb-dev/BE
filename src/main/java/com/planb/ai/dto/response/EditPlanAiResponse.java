package com.planb.ai.dto.response;

import java.util.List;

public record EditPlanAiResponse(
        String planName,
        List<CreatePlanAiResponse.PlanDayDetail> planDays,
        List<String> changes
) {
}