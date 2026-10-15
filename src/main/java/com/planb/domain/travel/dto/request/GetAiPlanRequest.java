package com.planb.domain.travel.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record GetAiPlanRequest(
        @Schema(description = "조회하거나 변경할 여행 ID", example = "1")
        Long travelId,
        @Schema(description = "확정·취소할 수정 요청 식별값, 생략 시 현재 요청")
        String editRequestId
) {

    public GetAiPlanRequest(Long travelId) {
        this(travelId, null);
    }

}
