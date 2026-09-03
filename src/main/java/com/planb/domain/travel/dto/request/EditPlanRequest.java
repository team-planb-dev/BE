package com.planb.domain.travel.dto.request;

public record EditPlanRequest(
        Long travelId,
        String editRequest
) {
}
