package com.planb.ai.context;

import com.planb.domain.travel.dto.request.CreateTravelRequest;

import java.util.List;

public record TravelPlanContext(CreateTravelRequest createTravelRequest,
                                List<TravelHealthContext> healthContexts) {
}
