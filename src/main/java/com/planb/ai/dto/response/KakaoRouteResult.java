package com.planb.ai.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.planb.global.client.kakaoMapService.dto.response.KakaoRouteFailure;

public record KakaoRouteResult(
        String origin,
        String destination,
        Integer distanceMeters,
        Integer travelMinutes,

        // 조회 실패의 내부 진단 정보, AI Tool 응답 제외
        @JsonIgnore
        KakaoRouteFailure failure
) {

    public KakaoRouteResult(
            String origin,
            String destination,
            Integer distanceMeters,
            Integer travelMinutes
    ) {

        this(
                origin,
                destination,
                distanceMeters,
                travelMinutes,
                null
        );
    }
}
