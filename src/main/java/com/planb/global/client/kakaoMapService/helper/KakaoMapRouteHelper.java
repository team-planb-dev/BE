package com.planb.global.client.kakaoMapService.helper;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPublicTrafficRouteResponse;
import org.springframework.stereotype.Component;

@Component
public class KakaoMapRouteHelper {

    // 카카오 대중교통 API의 도보권 경로 없음 상태값, 장애와 구분
    private static final String NO_TRANSIT_ROUTE_STATUS = "NO_RESULTS";

    private static final double EARTH_RADIUS_METERS = 6_371_000;

    // ponytail: 직선거리는 실제 보행로(강, 철로, 지하도)를 모른다. 우회 계수가 그 완충이며,
    //           보행로 거리 데이터 확보 시 실제 경로 길이로 대체
    private static final double WALKING_DETOUR_FACTOR = 1.3;

    // 고령자·질환자를 고려한 성인 평균보다 낮은 보행 속도
    private static final double WALKING_METERS_PER_MINUTE = 75;

    // 도보 제시가 비현실적인 구간의 거리 추정 제외
    private static final double WALKABLE_LIMIT_METERS = 1_500;

    /**
     * 경로 조회의 출발·도착 좌표
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
                || response
                        .documents()
                        .isEmpty()) {

            throw new IllegalStateException(
                    "카카오맵 장소 검색 결과 없음: "
                            + keyword
            );
        }

        return response
                .documents()
                .getFirst();
    }

    /**
     * 대중교통 경로 변환과 NO_RESULTS 도보권 추정
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
                route
                        .properties()
                        .totalDistance(),
                toMinutes(
                        route
                                .properties()
                                .totalTime()
                )
        );
    }

    // 대중교통 경로 첫 번째 값 조회
    private KakaoPublicTrafficRouteResponse.Route getFirstPublicTrafficRoute(KakaoPublicTrafficRouteResponse response) {
        if (!"OK".equals(response.status()) ||
                response.routes() == null ||
                response
                        .routes()
                        .isEmpty()) {
            throw new IllegalStateException("카카오맵 대중교통 경로 조회 결과 없음: " + response);
        }
        return response
                .routes()
                .getFirst();
    }

    // 좌표 누락·도보권 초과 시 기존 실패 처리 유지
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

    // 두 좌표 간 대권 거리, 좌표 누락·비숫자 시 null 반환
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
