package com.planb.domain.travel.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 공유 링크 발급 결과.
 *
 * 도메인 주소는 클라이언트가 알고 있으므로 토큰만 내려준다.
 *
 * @param shareToken 공유 링크 토큰
 */
public record ShareTravelResponse(

        @Schema(description = "공유한 여행 ID", example = "1")
        Long travelId,

        @Schema(description = "공유 조회 경로에 사용할 토큰")
        String shareToken
) {
}
