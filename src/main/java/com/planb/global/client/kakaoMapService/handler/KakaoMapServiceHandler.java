// src/main/java/com/planb/global/client/kakaoMapService/handler/KakaoMapServiceHandler.java
package com.planb.global.client.kakaoMapService.handler;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.global.client.kakaoMapService.KakaoMapServiceClient;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPublicTrafficRouteResponse;
import com.planb.global.client.kakaoMapService.helper.KakaoMapRouteHelper;
import com.planb.global.client.kakaoMapService.helper.KakaoPlaceSearchHelper;
import com.planb.global.client.kakaoMapService.helper.KakaoSearchKeywordSanitizer;
import com.planb.global.client.kakaoMobilityService.KakaoMobilityServiceClient;
import com.planb.global.client.kakaoMobilityService.dto.response.KakaoCarRouteResponse;
import com.planb.global.client.kakaoMobilityService.helper.KakaoMobilityRouteHelper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Component
@RequiredArgsConstructor
public class KakaoMapServiceHandler {

    private final KakaoMapServiceClient kakaoMapServiceClient;
    private final KakaoMobilityServiceClient kakaoMobilityServiceClient;

    private final KakaoMapRouteHelper kakaoMapRouteHelper;
    private final KakaoMobilityRouteHelper kakaoMobilityRouteHelper;
    private final KakaoSearchKeywordSanitizer kakaoSearchKeywordSanitizer;
    private final KakaoPlaceSearchHelper kakaoPlaceSearchHelper;

    // 카카오맵 API : 키워드 기반 장소 검색
    public Mono<KakaoPlaceSearchResponse> searchPlace(
            String keyword
    ) {

        String sanitizedKeyword = kakaoSearchKeywordSanitizer.sanitize(keyword);

        return kakaoMapServiceClient.get(
                uriBuilder -> uriBuilder
                        .path("/v2/local/search/keyword.json")
                        .queryParam(
                                "query",
                                sanitizedKeyword
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
                        .queryParam("start_x", startX)
                        .queryParam("start_y", startY)
                        .queryParam("end_x", endX)
                        .queryParam("end_y", endY)
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
                        .queryParam("origin", startX + "," + startY)
                        .queryParam("destination", endX + "," + endY)
                        .queryParam("priority", "TIME")
                        .queryParam("summary", true)
                        .build(),
                headers -> headers.set(
                        "Authorization",
                        "KakaoAK "
                                + kakaoMobilityServiceClient.serviceKey()
                ),
                KakaoCarRouteResponse.class
        );
    }

    // 장소 검색 후 이동수단에 따른 경로 조회.
    // 장소를 찾지 못하거나 경로 조회(대중교통/자동차)가 실패해도 예외를 던지지 않고,
    // travelMinutes/distanceMeters가 null인 결과로 대체합니다(STEP 7 정책).
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
                    kakaoMapRouteHelper.getFirstPlace(tuple.getT1(), origin);

            KakaoPlaceSearchResponse.Document end =
                    kakaoMapRouteHelper.getFirstPlace(tuple.getT2(), destination);

            return switch (transportation) {
                case TRANSIT ->
                        getPublicTrafficRoute(start.x(), start.y(), end.x(), end.y())
                                .map(response ->
                                        kakaoMapRouteHelper.makePublicTrafficRouteResult(origin, destination, response));
                case CAR ->
                        getCarRoute(start.x(), start.y(), end.x(), end.y())
                                .map(response ->
                                        kakaoMobilityRouteHelper.makeCarRouteResult(origin, destination, response));
            };
        }).onErrorResume(e ->
                Mono.just(
                        new KakaoRouteResult(origin, destination, null, null)
                )
        );
    }

    // 실제 장소(카페 또는 TourAPI에서 검색되지 않는 관광지) 존재 확인
    // + 이전 장소로부터의 이동시간 조회.
    // excludeNames와 일치하는 장소는 이미 사용된 것으로 간주해 found=false로 처리합니다.
    public Mono<PlaceWithRouteResult> findPlaceWithRoute(
            String keyword,
            String previousLocation,
            Transportation transportation,
            List<String> excludeNames
    ) {

        return searchPlace(keyword)
                .filter(kakaoPlaceSearchHelper::hasResult)
                .filter(response -> !kakaoPlaceSearchHelper.isExcluded(response, excludeNames))
                .flatMap(response ->
                        travelMinutesFrom(previousLocation, response, transportation)
                                .map(minutes -> kakaoPlaceSearchHelper.toResult(response, minutes))
                                .switchIfEmpty(Mono.fromSupplier(() ->
                                        kakaoPlaceSearchHelper.toResult(response, null)))
                )
                .defaultIfEmpty(kakaoPlaceSearchHelper.notFound());
    }

    // previousLocation이 있을 때만 실제 경로를 조회해 이동시간을 얻고,
    // 없거나 조회에 실패하면 빈 Mono를 반환
    private Mono<Integer> travelMinutesFrom(
            String previousLocation,
            KakaoPlaceSearchResponse response,
            Transportation transportation
    ) {

        return Mono.justOrEmpty(previousLocation)
                .filter(location -> !location.isBlank())
                .flatMap(location ->
                        getRoute(location, kakaoPlaceSearchHelper.firstPlaceName(response), transportation))
                .flatMap(route -> Mono.justOrEmpty(route.travelMinutes()))
                .onErrorResume(e -> Mono.empty());
    }
}