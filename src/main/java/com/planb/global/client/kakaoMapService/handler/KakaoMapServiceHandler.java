package com.planb.global.client.kakaoMapService.handler;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.global.client.kakaoMapService.KakaoMapServiceClient;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPublicTrafficRouteResponse;
import com.planb.global.client.kakaoMapService.helper.KakaoMapRouteHelper;
import com.planb.global.client.kakaoMobilityService.KakaoMobilityServiceClient;
import com.planb.global.client.kakaoMobilityService.dto.response.KakaoCarRouteResponse;
import com.planb.global.client.kakaoMobilityService.helper.KakaoMobilityRouteHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class KakaoMapServiceHandler {

    private final KakaoMapServiceClient kakaoMapServiceClient;
    private final KakaoMobilityServiceClient kakaoMobilityServiceClient;

    private final KakaoMapRouteHelper kakaoMapRouteHelper;
    private final KakaoMobilityRouteHelper kakaoMobilityRouteHelper;

    // 카카오맵 API : 키워드 기반 장소 검색
    public Mono<KakaoPlaceSearchResponse> searchPlace(
            String keyword
    ) {

        return kakaoMapServiceClient.get(
                uriBuilder -> uriBuilder
                        .path("/v2/local/search/keyword.json")
                        .queryParam(
                                "query",
                                keyword
                        )
                        .build(),
                headers -> headers.set(
                        "Authorization",
                        "KakaoAK "
                                + kakaoMapServiceClient.serviceKey()
                ),
                KakaoPlaceSearchResponse.class
        );
    }

    // 카카오맵 API : 대중교통 경로 조회
    public Mono<KakaoPublicTrafficRouteResponse> getPublicTrafficRoute(
            String startX,
            String startY,
            String endX,
            String endY
    ) {

        return kakaoMapServiceClient.get(
                uriBuilder -> uriBuilder
                        .path("/v2/routing/publictraffic")
                        .queryParam(
                                "start_x",
                                startX
                        )
                        .queryParam(
                                "start_y",
                                startY
                        )
                        .queryParam(
                                "end_x",
                                endX
                        )
                        .queryParam(
                                "end_y",
                                endY
                        )
                        .build(),
                headers -> headers.set(
                        "Authorization",
                        "KakaoAK "
                                + kakaoMapServiceClient.serviceKey()
                ),
                KakaoPublicTrafficRouteResponse.class
        );
    }

    // 카카오모빌리티 API : 자동차 경로 조회
    public Mono<KakaoCarRouteResponse> getCarRoute(
            String startX,
            String startY,
            String endX,
            String endY
    ) {

        return kakaoMobilityServiceClient.get(
                uriBuilder -> uriBuilder
                        .path("/v1/directions")
                        .queryParam(
                                "origin",
                                startX + "," + startY
                        )
                        .queryParam(
                                "destination",
                                endX + "," + endY
                        )
                        .queryParam(
                                "priority",
                                "TIME"
                        )
                        .queryParam(
                                "summary",
                                true
                        )
                        .build(),
                headers -> headers.set(
                        "Authorization",
                        "KakaoAK "
                                + kakaoMobilityServiceClient.serviceKey()
                ),
                KakaoCarRouteResponse.class
        );
    }

    // 장소 검색 후 이동수단에 따른 경로 조회
    public Mono<KakaoRouteResult> getRoute(
            String origin,
            String destination,
            Transportation transportation
    ) {

        return Mono.zip(
                searchPlace(origin),
                searchPlace(destination)
        ).flatMap(tuple -> {

            KakaoPlaceSearchResponse.Document start =
                    kakaoMapRouteHelper.getFirstPlace(
                            tuple.getT1(),
                            origin
                    );

            KakaoPlaceSearchResponse.Document end =
                    kakaoMapRouteHelper.getFirstPlace(
                            tuple.getT2(),
                            destination
                    );

            return switch (transportation) {

                // 대중 교통 선택시
                case TRANSIT ->
                        getPublicTrafficRoute(
                                start.x(),
                                start.y(),
                                end.x(),
                                end.y()
                        )
                                .map(response ->
                                        kakaoMapRouteHelper
                                                .makePublicTrafficRouteResult(
                                                        origin,
                                                        destination,
                                                        response
                                                )
                                );

                // 차 선택시
                case CAR ->
                        getCarRoute(
                                start.x(),
                                start.y(),
                                end.x(),
                                end.y()
                        )
                                .map(response ->
                                        kakaoMobilityRouteHelper
                                                .makeCarRouteResult(
                                                        origin,
                                                        destination,
                                                        response
                                                )
                                );
            };
        });
    }
}