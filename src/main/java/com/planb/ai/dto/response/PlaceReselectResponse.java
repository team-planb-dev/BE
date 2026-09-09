package com.planb.ai.dto.response;

import com.planb.ai.dto.response.CreatePlanAiResponse.RestaurantDetail;

public record PlaceReselectResponse(
        String selectedCandidateId,
        RestaurantDetail restaurantDetail
) { }
