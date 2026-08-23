package com.planb.ai.dto.response;

public record KakaoRouteResult(
        String origin,
        String destination,
        Integer distanceMeters,
        Integer travelMinutes) {
}