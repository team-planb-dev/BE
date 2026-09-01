package com.planb.global.client.kakaoMobilityService.dto.response;

import java.util.List;

public record KakaoCarRouteResponse
        (String trans_id,
         List<Route> routes) {

    public record Route
            (Integer result_code,
             String result_msg,
             Summary summary) {
    }

    public record Summary
            (Point origin,
             Point destination,
             String priority,
             Fare fare,
             Integer distance, // 미터(m) 단위
             Integer duration) { // 초(sec) 단위
    }

    public record Point
            (String name,
             Double x,
             Double y) {
    }

    public record Fare
            (Integer taxi,
             Integer toll) {
    }
}