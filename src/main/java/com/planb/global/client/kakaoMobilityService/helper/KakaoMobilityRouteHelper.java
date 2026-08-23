package com.planb.global.client.kakaoMobilityService.helper;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.global.client.kakaoMobilityService.dto.response.KakaoCarRouteResponse;
import org.springframework.stereotype.Component;

@Component
public class KakaoMobilityRouteHelper {

    // 자동차 API 응답을 Tool 응답으로 변환
    public KakaoRouteResult makeCarRouteResult(
            String origin,
            String destination,
            KakaoCarRouteResponse response
    ) {

        KakaoCarRouteResponse.Summary summary =
                getFirstCarRouteSummary(
                        response
                );

        return new KakaoRouteResult(
                origin,
                destination,
                summary.distance(),
                toMinutes(
                        summary.duration()
                )
        );
    }

    // 자동차 첫 번째 경로 Summary 조회
    private KakaoCarRouteResponse.Summary
    getFirstCarRouteSummary(
            KakaoCarRouteResponse response
    ) {

        if (response.routes() == null
                || response.routes().isEmpty()) {

            throw new IllegalStateException(
                    "카카오모빌리티 자동차 경로 조회 결과 없음"
            );
        }

        KakaoCarRouteResponse.Route route =
                response.routes()
                        .getFirst();

        if (route.result_code() == null
                || route.result_code() != 0
                || route.summary() == null) {

            throw new IllegalStateException(
                    "카카오모빌리티 자동차 경로 조회 실패"
            );
        }

        return route.summary();
    }

    // 초 -> 분 변환
    private Integer toMinutes(
            Integer seconds
    ) {

        return (int) Math.ceil(
                seconds / 60.0
        );
    }
}