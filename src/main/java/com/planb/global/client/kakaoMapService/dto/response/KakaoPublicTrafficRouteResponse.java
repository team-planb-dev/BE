package com.planb.global.client.kakaoMapService.dto.response;

import java.util.List;

public record KakaoPublicTrafficRouteResponse(
        String status,
        Properties properties,
        List<Route> routes
) {

    public record Properties(
            Integer total,
            Integer bus,
            Integer subway,
            Integer busAndSubway,
            String landingURL
    ) {
    }

    public record Route(
            RouteProperties properties
    ) {
    }

    public record RouteProperties(
            String type,
            Integer totalDistance,
            Integer totalTime,
            Integer transfers,
            Fare fare
    ) {
    }

    public record Fare(
            Integer value,
            Integer min,
            Integer max
    ) {
    }
}