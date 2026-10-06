package com.planb.domain.travel.policy;

import java.util.Optional;

/**
 * 경로 조회 실패 구간의 근거리 도보 시간 추정
 *
 * 직선거리 500m 이하만 추정, 그 외는 명시적 실패 계약(#66·#67) 유지
 * 도로 우회 계수 1.3, 보행 속도 67m/분(4km/h), 최소 1분
 */
public final class NearbyWalkEstimate {

    private static final double MAX_DISTANCE_METERS = 500.0;

    private static final double DETOUR_FACTOR = 1.3;

    private static final double WALK_METERS_PER_MINUTE = 67.0;

    private static final double EARTH_RADIUS_METERS = 6_371_000.0;

    private NearbyWalkEstimate() {
    }

    public static Optional<Integer> minutes(
            String originLongitude,
            String originLatitude,
            String destinationLongitude,
            String destinationLatitude
    ) {

        try {
            double distance = haversineMeters(
                    Double.parseDouble(originLongitude),
                    Double.parseDouble(originLatitude),
                    Double.parseDouble(destinationLongitude),
                    Double.parseDouble(destinationLatitude)
            );

            if (distance > MAX_DISTANCE_METERS) {
                return Optional.empty();
            }

            int minutes = (int) Math.ceil(distance * DETOUR_FACTOR / WALK_METERS_PER_MINUTE);

            return Optional.of(Math.max(
                    1,
                    minutes
            ));
        } catch (NullPointerException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static double haversineMeters(
            double originLongitude,
            double originLatitude,
            double destinationLongitude,
            double destinationLatitude
    ) {

        double originLat = Math.toRadians(originLatitude);
        double destinationLat = Math.toRadians(destinationLatitude);
        double deltaLat = destinationLat - originLat;
        double deltaLon = Math.toRadians(destinationLongitude - originLongitude);

        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(originLat) * Math.cos(destinationLat) * Math.pow(Math.sin(deltaLon / 2), 2);

        return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(a));
    }
}
