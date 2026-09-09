package com.planb.domain.travel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record GetAiPlanRequest(
        @Schema(description = "조회하거나 변경할 여행 ID", example = "1")
        Long travelId
) {
}
