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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
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

        return getRoute(origin, destination, transportation, null, null, null, null);
    }

    // 확정된 좌표를 우선 사용하고, 좌표가 없는 지점만 이름으로 검색한다.
    public Mono<KakaoRouteResult> getRoute(
            String origin,
            String destination,
            Transportation transportation,
            String originX,
            String originY,
            String destinationX,
            String destinationY
    ) {

        return Mono
                .zip(
                        routeCoordinates(origin, originX, originY),
                        routeCoordinates(destination, destinationX, destinationY))
                .flatMap(points -> {
                    List<String> start = points.getT1();
                    List<String> end = points.getT2();

                    return switch (transportation) {
                        case TRANSIT -> getPublicTrafficRoute(start.get(0), start.get(1), end.get(0), end.get(1))
                                .map(response -> kakaoMapRouteHelper.makePublicTrafficRouteResult(
                                        origin,
                                        destination,
                                        response,
                                        new KakaoMapRouteHelper.RoutePoints(
                                                start.get(0),
                                                start.get(1),
                                                end.get(0),
                                                end.get(1))));
                        case CAR -> getCarRoute(start.get(0), start.get(1), end.get(0), end.get(1))
                                .map(response -> kakaoMobilityRouteHelper.makeCarRouteResult(origin, destination, response));
                    };
                })
                .switchIfEmpty(Mono.error(new IllegalStateException("경로 조회 응답 없음")))
                .map(route -> {
                    if (route.travelMinutes() == null || route.travelMinutes() < 0) {
                        throw new IllegalStateException("유효한 경로 이동시간 없음: " + route);
                    }
                    return route;
                })
                .onErrorResume(exception -> {
                    log.warn(
                            "[ROUTE LOOKUP FAILED] origin={}, destination={}, transportation={}, originX={}, originY={}, destinationX={}, destinationY={}",
                            origin,
                            destination,
                            transportation,
                            originX,
                            originY,
                            destinationX,
                            destinationY,
                            exception);

                    return Mono.just(new KakaoRouteResult(origin, destination, null, null));
                });
    }

    private Mono<List<String>> routeCoordinates(
            String name,
            String x,
            String y
    ) {

        return Mono.defer(() -> {
            if (x != null && !x.isBlank() && y != null && !y.isBlank()) {
                return Mono.just(List.of(x, y));
            }
            if (name == null || name.isBlank()) {
                return Mono.error(new IllegalArgumentException("경로 조회 지점의 이름과 좌표 누락"));
            }
            return searchPlace(name)
                    .map(response -> kakaoMapRouteHelper.getFirstPlace(response, name))
                    .map(place -> List.of(place.x(), place.y()))
                    .switchIfEmpty(Mono.error(new IllegalStateException("경로 조회 지점 검색 결과 없음: " + name)));
        });
    }

    // 실제 장소(카페 또는 TourAPI에서 검색되지 않는 관광지) 존재 확인
    // + 이전 장소로부터의 이동시간 조회.
    // excludeNames와 일치하는 장소는 이미 사용된 것으로 간주, found=false로 처리
    public Mono<PlaceWithRouteResult> findPlaceWithRoute(
            String keyword,
            String previousLocation,
            Transportation transportation,
            List<String> excludeNames,
            String categoryCode,
            String previousLongitude,
            String previousLatitude
    ) {

        return searchPlace(keyword)
                .map(response -> kakaoPlaceSearchHelper.filterByCategory(
                        response,
                        categoryCode
                ))
                .filter(kakaoPlaceSearchHelper::hasResult)
                .filter(response -> !kakaoPlaceSearchHelper.isExcluded(response, excludeNames))
                .flatMap(response ->
                        travelMinutesFrom(
                                previousLocation,
                                previousLongitude,
                                previousLatitude,
                                response,
                                transportation)
                                .map(minutes -> kakaoPlaceSearchHelper.toResult(response, minutes))
                                .switchIfEmpty(Mono.fromSupplier(() ->
                                        kakaoPlaceSearchHelper.toResult(response, null)))
                )
                .defaultIfEmpty(kakaoPlaceSearchHelper.notFound());
    }

    /**
     * previousLocation이 있을 때만 실제 경로를 조회해 이동시간을 얻고,
     * 없거나 조회에 실패하면 빈 Mono를 반환한다.
     *
     * 출발지는 이번 호출에서 확정한 좌표를, 도착지는 방금 검색한 결과의 좌표를 그대로 쓴다.
     * 이름으로 다시 검색하면 카카오가 전국에서 동명 장소를 잡아 엉뚱한 좌표가 되고,
     * 검색 과정에서 장소명 자체가 바뀌어 있어 재검색 결과가 원래 장소와 달라진다.
     */
    private Mono<Integer> travelMinutesFrom(
            String previousLocation,
            String previousLongitude,
            String previousLatitude,
            KakaoPlaceSearchResponse response,
            Transportation transportation
    ) {

        return Mono.justOrEmpty(previousLocation)
                .filter(location -> !location.isBlank())
                .flatMap(location ->
                        getRoute(
                                location,
                                kakaoPlaceSearchHelper.firstPlaceName(response),
                                transportation,
                                previousLongitude,
                                previousLatitude,
                                kakaoPlaceSearchHelper.firstPlaceLongitude(response),
                                kakaoPlaceSearchHelper.firstPlaceLatitude(response)))
                .flatMap(route -> Mono.justOrEmpty(route.travelMinutes()))
                .onErrorResume(e -> Mono.empty());
    }
}
