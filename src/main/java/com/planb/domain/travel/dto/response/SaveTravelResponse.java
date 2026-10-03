package com.planb.domain.travel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 여행 일정 저장 확정 결과, 저장 후 saved는 true
 */
public record SaveTravelResponse(

        @Schema(description = "저장한 여행 ID", example = "1")
        Long travelId,

        @Schema(description = "저장 확정 여부입니다. 성공 응답에서는 true입니다.", example = "true")
        boolean saved
) {
}
