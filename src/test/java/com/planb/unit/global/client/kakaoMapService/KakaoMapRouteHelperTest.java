package com.planb.unit.global.client.kakaoMapService;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPublicTrafficRouteResponse;
import com.planb.global.client.kakaoMapService.helper.KakaoMapRouteHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KakaoMapRouteHelperTest {

    private final KakaoMapRouteHelper kakaoMapRouteHelper =
            new KakaoMapRouteHelper();

    @Test
    @DisplayName("장소 검색 첫 번째 결과 조회")
    void getFirstPlace() {

        KakaoPlaceSearchResponse.Document first =
                makeDocument(
                        "1",
                        "경복궁"
                );

        KakaoPlaceSearchResponse.Document second =
                makeDocument(
                        "2",
                        "창덕궁"
                );

        KakaoPlaceSearchResponse response =
                new KakaoPlaceSearchResponse(
                        new KakaoPlaceSearchResponse.Meta(
                                2,
                                2,
                                true
                        ),
                        List.of(
                                first,
                                second
                        )
                );

        KakaoPlaceSearchResponse.Document result =
                kakaoMapRouteHelper.getFirstPlace(
                        response,
                        "궁궐"
                );

        assertEquals(
                first,
                result
        );
    }

    @Test
    @DisplayName("장소 검색 결과 없음 예외")
    void getFirstPlaceException() {

        KakaoPlaceSearchResponse response =
                new KakaoPlaceSearchResponse(
                        new KakaoPlaceSearchResponse.Meta(
                                0,
                                0,
                                true
                        ),
                        List.of()
                );

        assertThrows(
                IllegalStateException.class,
                () -> kakaoMapRouteHelper.getFirstPlace(
                        response,
                        "없는 장소"
                )
        );
    }

    @Test
    @DisplayName("대중교통 경로 결과 변환")
    void makePublicTrafficRouteResult() {

        KakaoPublicTrafficRouteResponse.RouteProperties properties =
                new KakaoPublicTrafficRouteResponse.RouteProperties(
                        "PUBLIC_TRANSPORT",
                        12500,
                        601,
                        1,
                        new KakaoPublicTrafficRouteResponse.Fare(
                                1500,
                                1500,
                                1500
                        )
                );

        KakaoPublicTrafficRouteResponse response =
                new KakaoPublicTrafficRouteResponse(
                        "OK",
                        null,
                        List.of(
                                new KakaoPublicTrafficRouteResponse.Route(
                                        properties
                                )
                        )
                );

        KakaoRouteResult result =
                kakaoMapRouteHelper.makePublicTrafficRouteResult(
                        "경복궁",
                        "남산서울타워",
                        response,
                        points()
                );

        assertEquals(
                "경복궁",
                result.origin()
        );

        assertEquals(
                "남산서울타워",
                result.destination()
        );

        assertEquals(
                12500,
                result.distanceMeters()
        );

        assertEquals(
                11,
                result.travelMinutes()
        );
    }

    @Test
    @DisplayName("대중교통 경로 상태 오류 예외")
    void publicTrafficRouteStatusException() {

        KakaoPublicTrafficRouteResponse response =
                new KakaoPublicTrafficRouteResponse(
                        "ERROR",
                        null,
                        List.of()
                );

        assertThrows(
                IllegalStateException.class,
                () ->
                        kakaoMapRouteHelper
                                .makePublicTrafficRouteResult(
                                        "경복궁",
                                        "남산서울타워",
                                        response,
                                        points()
                                )
        );
    }

    @Test
    @DisplayName("대중교통 경로 결과 없음 예외")
    void publicTrafficRouteEmptyException() {

        KakaoPublicTrafficRouteResponse response =
                new KakaoPublicTrafficRouteResponse(
                        "OK",
                        null,
                        List.of()
                );

        assertThrows(
                IllegalStateException.class,
                () ->
                        kakaoMapRouteHelper
                                .makePublicTrafficRouteResult(
                                        "경복궁",
                                        "남산서울타워",
                                        response,
                                        points()
                                )
        );
    }

    @Test
    @DisplayName("도보권 대중교통 경로 없음의 도보 시간 추정")
    void estimatesWalkingWhenNoTransitRouteWithinWalkableDistance() {

        // 경복궁 -> 스타벅스 경복궁역점. 직선 약 375m 구간이라 대중교통 경로가 없다.
        KakaoRouteResult result =
                kakaoMapRouteHelper.makePublicTrafficRouteResult(
                        "경복궁",
                        "스타벅스 경복궁역점",
                        noResults(),
                        new KakaoMapRouteHelper.RoutePoints(
                                "126.97689786832184",
                                "37.577613288258206",
                                "126.972676532641",
                                "37.5772042362666"
                        )
                );

        assertEquals(
                7,
                result.travelMinutes()
        );

        assertTrue(
                result.distanceMeters() >= 480
                        && result.distanceMeters() <= 495,
                "우회 보정 거리: " + result.distanceMeters()
        );
    }

    @Test
    @DisplayName("도보권을 넘는 대중교통 경로 없음의 추정 포기")
    void failsWhenNoTransitRouteBeyondWalkableDistance() {

        // 직선 약 2.6km. 도보로 제시하면 일정 자체가 비현실적이 된다.
        assertThrows(
                IllegalStateException.class,
                () -> kakaoMapRouteHelper.makePublicTrafficRouteResult(
                        "출발",
                        "도착",
                        noResults(),
                        new KakaoMapRouteHelper.RoutePoints(
                                "126.90",
                                "37.50",
                                "126.93",
                                "37.50"
                        )
                )
        );
    }

    @Test
    @DisplayName("좌표 없는 대중교통 경로 없음의 추정 불가")
    void failsWhenNoTransitRouteWithoutCoordinates() {

        assertThrows(
                IllegalStateException.class,
                () -> kakaoMapRouteHelper.makePublicTrafficRouteResult(
                        "출발",
                        "도착",
                        noResults(),
                        points()
                )
        );
    }

    private KakaoPublicTrafficRouteResponse noResults() {

        return new KakaoPublicTrafficRouteResponse(
                "NO_RESULTS",
                null,
                List.of()
        );
    }

    private KakaoMapRouteHelper.RoutePoints points() {

        return new KakaoMapRouteHelper.RoutePoints(
                null,
                null,
                null,
                null
        );
    }

    private KakaoPlaceSearchResponse.Document makeDocument(
            String id,
            String placeName
    ) {

        return new KakaoPlaceSearchResponse.Document(
                id,
                placeName,
                null,
                null,
                null,
                null,
                null,
                null,
                "127.0",
                "37.0",
                null,
                null
        );
    }
}