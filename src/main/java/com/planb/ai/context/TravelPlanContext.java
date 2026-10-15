package com.planb.ai.context;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.planb.domain.travel.dto.request.CreateTravelRequest;

import java.util.List;

public record TravelPlanContext(
        CreateTravelRequest createTravelRequest,
        List<TravelHealthContext> healthContexts,
        @JsonIgnore AiCallBudget callBudget
) {

    public TravelPlanContext(CreateTravelRequest request, List<TravelHealthContext> healthContexts) {

        this(request, healthContexts, new AiCallBudget());
    }
}
