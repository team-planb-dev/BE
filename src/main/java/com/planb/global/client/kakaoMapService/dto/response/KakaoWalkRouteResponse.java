package com.planb.global.client.kakaoMapService.dto.response;

import java.util.List;

public record KakaoWalkRouteResponse(
        List<Route> routes
) {

    public record Route(
            Integer resultCode,
            String resultMsg,
            Summary summary,
            List<Section> sections
    ) {
    }

    public record Summary(
            Origin origin,
            Destination destination,
            Integer distance,
            Integer duration
    ) {
    }

    public record Origin(
            String name,
            Double x,
            Double y
    ) {
    }

    public record Destination(
            String name,
            Double x,
            Double y
    ) {
    }

    public record Section(
            Integer distance,
            Integer duration
    ) {
    }
}