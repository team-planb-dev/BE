package com.planb.integration.external.kakaoMapService;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.integration.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카카오맵 및 카카오모빌리티 API Handler 통합 테스트
 *
 * 실제 외부 API 기반 장소 검색 및 응답 파싱 검증.
 * 대중교통 경로 조회 및 응답 파싱 검증.
 * 자동차 경로 조회 및 응답 파싱 검증.
 * 이동수단별 KakaoRouteResult 변환 로직 검증.
 */
class KakaoMapServiceHandlerTest extends IntegrationTest {

    @Autowired
    private KakaoMapServiceHandler kakaoMapServiceHandler;

    @Test
    @DisplayName("카카오 장소 검색 API 호출 및 응답 파싱")
    void searchPlace() {

        KakaoPlaceSearchResponse response = kakaoMapServiceHandler
                .searchPlace("강남역")
                .block();

        assertThat(response)
                .isNotNull();

        assertThat(response.meta())
                .isNotNull();

        assertThat(response.documents())
                .isNotEmpty();

        KakaoPlaceSearchResponse.Document firstPlace = response
                .documents()
                .getFirst();

        assertThat(firstPlace.place_name())
                .isNotBlank();

        assertThat(firstPlace.x())
                .isNotBlank();

        assertThat(firstPlace.y())
                .isNotBlank();
    }

    @Test
    @DisplayName("카카오 자동차 경로 API 호출 및 응답 파싱")
    void getCarRoute() {

        KakaoRouteResult response = kakaoMapServiceHandler
                .getRoute(
                        "강남역",
                        "서울역",
                        Transportation.CAR
                )
                .block();

        assertThat(response)
                .isNotNull();

        assertThat(response.distanceMeters())
                .isPositive();

        assertThat(response.travelMinutes())
                .isPositive();
    }

    @Test
    @DisplayName("카카오 대중교통 경로 API 호출 및 응답 파싱")
    void getPublicTrafficRoute() {

        KakaoRouteResult response = kakaoMapServiceHandler
                .getRoute(
                        "강남역",
                        "서울역",
                        Transportation.TRANSIT
                )
                .block();

        assertThat(response)
                .isNotNull();

        assertThat(response.distanceMeters())
                .isPositive();

        assertThat(response.travelMinutes())
                .isPositive();
    }
}