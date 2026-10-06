package com.planb.unit.domain.travel.policy;

import com.planb.domain.travel.policy.NearbyWalkEstimate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NearbyWalkTestEstimate {

    @Test
    @DisplayName("기준선 C4 경로 조회 실패 구간(약 70m)은 도보 2분으로 추정")
    void estimatesWalkForShortDistance() {

        // 경주 김유신묘 → 금산재 칼국수, haversine 69.7m → ceil(69.7 × 1.3 ÷ 67) = 2
        assertEquals(
                Optional.of(2),
                NearbyWalkEstimate.minutes(
                        "129.1923886866",
                        "35.8453311317",
                        "129.1928553267",
                        "35.8448311971"
                )
        );
    }

    @Test
    @DisplayName("직선거리 500m 초과 구간은 추정하지 않음")
    void doesNotEstimateBeyondLimit() {

        // 위도 0.01도 차이는 약 1.1km
        assertEquals(
                Optional.empty(),
                NearbyWalkEstimate.minutes(
                        "129.0000000000",
                        "35.0000000000",
                        "129.0000000000",
                        "35.0100000000"
                )
        );
    }

    @Test
    @DisplayName("같은 좌표는 최소 1분")
    void usesAtLeastOneMinute() {

        assertEquals(
                Optional.of(1),
                NearbyWalkEstimate.minutes(
                        "127.0",
                        "37.5",
                        "127.0",
                        "37.5"
                )
        );
    }

    @Test
    @DisplayName("좌표가 없거나 숫자가 아니면 추정하지 않음")
    void doesNotEstimateWithoutCoordinates() {

        assertEquals(
                Optional.empty(),
                NearbyWalkEstimate.minutes(
                        null,
                        "37.5",
                        "127.0",
                        "37.5"
                )
        );

        assertEquals(
                Optional.empty(),
                NearbyWalkEstimate.minutes(
                        "abc",
                        "37.5",
                        "127.0",
                        "37.5"
                )
        );
    }
}
