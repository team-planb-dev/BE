package com.planb.global.client.kakaoMapService.helper;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPublicTrafficRouteResponse;
import org.springframework.stereotype.Component;

@Component
public class KakaoMapRouteHelper {

    // 카카오 대중교통 API가 도보권 구간에 돌려주는 상태값. 장애가 아니라 "경로가 없음"을 뜻한다.
    private static final String NO_TRANSIT_ROUTE_STATUS = "NO_RESULTS";

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    // ponytail: 직선거리는 실제 보행로(강, 철로, 지하도)를 모른다. 우회 계수가 그 완충이며,
    //           보행로 거리 데이터를 얻게 되면 이 계수 대신 실제 경로 길이를 쓴다.
    private static final double WALKING_DETOUR_FACTOR = 1.3;

    // 동행인에 고령자·질환자가 포함되는 서비스라 성인 평균(약 5km/h)보다 느리게 잡는다.
    private static final double WALKING_METERS_PER_MINUTE = 75;

    // 이 거리를 넘으면 도보로 제시하는 것 자체가 비현실적이므로 추정하지 않는다.
    private static final double WALKABLE_LIMIT_METERS = 1_500;

    /**
     * 경로 조회의 출발·도착 좌표.
     *
     * 대중교통 경로가 없을 때 도보 시간을 추정하려면 좌표가 필요하다.
     * 네 개가 모두 같은 타입이라 순서를 바꿔도 컴파일이 통과하므로 이름으로 자리를 보증한다.
     */
    public record RoutePoints(

            String startX,
            String startY,
            String endX,
            String endY
    ) {
    }

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

    /**
     * 대중교통 경로 API 응답을 Tool 응답으로 변환한다.
     *
     * 도보권 구간은 대중교통 경로가 존재하지 않아 NO_RESULTS가 돌아온다.
     * 이때만 좌표로 도보 시간을 추정하고, 다른 실패는 그대로 예외로 남긴다.
     */
    public KakaoRouteResult makePublicTrafficRouteResult(
            String origin,
            String destination,
            KakaoPublicTrafficRouteResponse response,
            RoutePoints points
    ) {

        if (NO_TRANSIT_ROUTE_STATUS.equals(response.status())) {
            return makeWalkingRouteResult(
                    origin,
                    destination,
                    response,
                    points
            );
        }

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
    private KakaoPublicTrafficRouteResponse.Route getFirstPublicTrafficRoute(KakaoPublicTrafficRouteResponse response) {
        if (!"OK".equals(response.status()) ||
                response.routes() == null ||
                response.routes().isEmpty()) {
            throw new IllegalStateException("카카오맵 대중교통 경로 조회 결과 없음: " + response);
        }
        return response.routes().getFirst();
    }

    // 좌표가 없거나 도보권을 넘으면 추정하지 않고 기존과 같이 실패로 남긴다.
    private KakaoRouteResult makeWalkingRouteResult(
            String origin,
            String destination,
            KakaoPublicTrafficRouteResponse response,
            RoutePoints points
    ) {

        Double straightLine = straightLineMeters(points);

        if (straightLine == null || straightLine > WALKABLE_LIMIT_METERS) {
            throw new IllegalStateException("카카오맵 대중교통 경로 조회 결과 없음: " + response);
        }

        int walkingDistance = (int) Math.round(straightLine * WALKING_DETOUR_FACTOR);

        return new KakaoRouteResult(
                origin,
                destination,
                walkingDistance,
                (int) Math.ceil(walkingDistance / WALKING_METERS_PER_MINUTE)
        );
    }

    // 두 좌표 사이의 대권 거리. 좌표가 없거나 숫자가 아니면 추정 불가로 보고 null을 돌려준다.
    private Double straightLineMeters(RoutePoints points) {

        if (points == null) {
            return null;
        }

        Double startX = coordinate(points.startX());
        Double startY = coordinate(points.startY());
        Double endX = coordinate(points.endX());
        Double endY = coordinate(points.endY());

        if (startX == null || startY == null || endX == null || endY == null) {
            return null;
        }

        double latitudeDelta = Math.toRadians(endY - startY);
        double longitudeDelta = Math.toRadians(endX - startX);

        double a = Math.pow(Math.sin(latitudeDelta / 2), 2)
                + Math.cos(Math.toRadians(startY))
                        * Math.cos(Math.toRadians(endY))
                        * Math.pow(Math.sin(longitudeDelta / 2), 2);

        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }

    private Double coordinate(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Double.valueOf(value);
        } catch (NumberFormatException exception) {
            return null;
        }
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
