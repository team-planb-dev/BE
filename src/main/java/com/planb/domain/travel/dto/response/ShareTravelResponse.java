package com.planb.domain.travel.dto.response;

/**
 * 공유 링크 발급 결과.
 *
 * 도메인 주소는 클라이언트가 알고 있으므로 토큰만 내려준다.
 *
 * @param shareToken 공유 링크 토큰
 */
public record ShareTravelResponse(

        Long travelId,
        String shareToken) {
}
