package com.planb.unit.global.client.kakaoMobilityService;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.global.client.kakaoMobilityService.dto.response.KakaoCarRouteResponse;
import com.planb.global.client.kakaoMobilityService.helper.KakaoMobilityRouteHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KakaoMobilityRouteHelperTest {

    private final KakaoMobilityRouteHelper kakaoMobilityRouteHelper =
            new KakaoMobilityRouteHelper();

    @Test
    @DisplayName("자동차 경로 결과 변환")
    void makeCarRouteResult() {

        KakaoCarRouteResponse.Summary summary =
                new KakaoCarRouteResponse.Summary(
                        new KakaoCarRouteResponse.Point(
                                "경복궁",
                                126.0,
                                37.0
                        ),
                        new KakaoCarRouteResponse.Point(
                                "남산서울타워",
                                127.0,
                                37.0
                        ),
                        "TIME",
                        new KakaoCarRouteResponse.Fare(
                                10000,
                                1000
                        ),
                        15000,
                        601
                );

        KakaoCarRouteResponse response =
                new KakaoCarRouteResponse(
                        "transaction-id",
                        List.of(
                                new KakaoCarRouteResponse.Route(
                                        0,
                                        "정상",
                                        summary
                                )
                        )
                );

        KakaoRouteResult result =
                kakaoMobilityRouteHelper.makeCarRouteResult(
                        "경복궁",
                        "남산서울타워",
                        response
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
                15000,
                result.distanceMeters()
        );

        assertEquals(
                11,
                result.travelMinutes()
        );
    }

    @Test
    @DisplayName("자동차 경로 결과 없음 예외")
    void carRouteEmptyException() {

        KakaoCarRouteResponse response =
                new KakaoCarRouteResponse(
                        "transaction-id",
                        List.of()
                );

        assertThrows(
                IllegalStateException.class,
                () ->
                        kakaoMobilityRouteHelper
                                .makeCarRouteResult(
                                        "경복궁",
                                        "남산서울타워",
                                        response
                                )
        );
    }

    @Test
    @DisplayName("자동차 경로 결과 코드 오류 예외")
    void carRouteResultCodeException() {

        KakaoCarRouteResponse response =
                new KakaoCarRouteResponse(
                        "transaction-id",
                        List.of(
                                new KakaoCarRouteResponse.Route(
                                        104,
                                        "경로 탐색 실패",
                                        null
                                )
                        )
                );

        assertThrows(
                IllegalStateException.class,
                () ->
                        kakaoMobilityRouteHelper
                                .makeCarRouteResult(
                                        "경복궁",
                                        "남산서울타워",
                                        response
                                )
        );
    }

    @Test
    @DisplayName("자동차 경로 Summary 없음 예외")
    void carRouteSummaryNullException() {

        KakaoCarRouteResponse response =
                new KakaoCarRouteResponse(
                        "transaction-id",
                        List.of(
                                new KakaoCarRouteResponse.Route(
                                        0,
                                        "정상",
                                        null
                                )
                        )
                );

        assertThrows(
                IllegalStateException.class,
                () ->
                        kakaoMobilityRouteHelper
                                .makeCarRouteResult(
                                        "경복궁",
                                        "남산서울타워",
                                        response
                                )
        );
    }
}