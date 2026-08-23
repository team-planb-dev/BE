package com.planb.global.client.kakaoMapService.helper;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPublicTrafficRouteResponse;
import org.springframework.stereotype.Component;

@Component
public class KakaoMapRouteHelper {

    // 장소 검색 결과 첫 번째 값 조회
    public KakaoPlaceSearchResponse.Document getFirstPlace(
            KakaoPlaceSearchResponse response,
            String keyword
    ) {

        if (response.documents() == null
                || response.documents().isEmpty()) {

            throw new IllegalStateException(
                    "카카오맵 장소 검색 결과 없음: "
                            + keyword
            );
        }

        return response.documents()
                .getFirst();
    }

    // 대중교통 경로 API 응답을 Tool 응답으로 변환
    public KakaoRouteResult makePublicTrafficRouteResult(
            String origin,
            String destination,
            KakaoPublicTrafficRouteResponse response
    ) {

        KakaoPublicTrafficRouteResponse.Route route =
                getFirstPublicTrafficRoute(
                        response
                );

        return new KakaoRouteResult(
                origin,
                destination,
                route.properties()
                        .totalDistance(),
                toMinutes(
                        route.properties()
                                .totalTime()
                )
        );
    }

    // 대중교통 경로 첫 번째 값 조회
    private KakaoPublicTrafficRouteResponse.Route
    getFirstPublicTrafficRoute(
            KakaoPublicTrafficRouteResponse response
    ) {

        if (!"OK".equals(response.status())
                || response.routes() == null
                || response.routes().isEmpty()) {

            throw new IllegalStateException(
                    "카카오맵 대중교통 경로 조회 결과 없음"
            );
        }

        return response.routes()
                .getFirst();
    }

    // 초 단위 이동시간을 분 단위로 변환
    private Integer toMinutes(
            Integer totalTime
    ) {

        return (int) Math.ceil(
                totalTime / 60.0
        );
    }
}