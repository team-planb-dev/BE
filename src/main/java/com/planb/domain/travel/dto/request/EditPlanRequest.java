package com.planb.domain.travel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record EditPlanRequest(
        @Schema(description = "수정할 여행 ID", example = "1")
        Long travelId,

        @Schema(description = "AI가 해석할 자연어 일정 수정 요청", example = "첫째 날 카페 일정을 다른 장소로 바꿔 주세요.")
        String editRequest
) {
}
