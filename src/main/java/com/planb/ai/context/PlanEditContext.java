package com.planb.ai.context;

import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;

import java.util.List;

public record PlanEditContext(
        CreateTravelRequest createTravelRequest,
        List<TravelHealthContext> healthContexts,
        GetAiPlanResponse currentPlan,
        String editRequest
) {
}