package com.planb.unit.global.ai.tool;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.service.NutritionService;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TourismToolTest {

    @Mock
    private Kor2ServiceHandler kor2ServiceHandler;

    @Mock
    private KakaoMapServiceHandler kakaoMapServiceHandler;

    @Mock
    private NutritionService nutritionService;

    @Mock
    private NutritionEvaluationCollector nutritionEvaluationCollector;

    @InjectMocks
    private TourismTool tourismTool;

    @Test
    @DisplayName("지역 기반 관광정보 검색 위임")
    void searchTourismByLocation() {

        // given
        String keyword = "돼지국밥";
        String locationDo = "부산광역시";
        String locationSigungu = "해운대구";
        Integer contentTypeId = 39;

        Kor2KeywordSearchResponse response =
                org.mockito.Mockito.mock(
                        Kor2KeywordSearchResponse.class
                );

        when(kor2ServiceHandler.searchKeyword(
                keyword,
                locationDo,
                locationSigungu,
                contentTypeId
        )).thenReturn(Mono.just(response));

        // when
        Kor2KeywordSearchResponse result =
                tourismTool.searchTourismByLocation(
                        keyword,
                        locationDo,
                        locationSigungu,
                        contentTypeId
                );

        // then
        assertEquals(response, result);

        verify(kor2ServiceHandler)
                .searchKeyword(
                        keyword,
                        locationDo,
                        locationSigungu,
                        contentTypeId
                );
    }

    @Test
    @DisplayName("장소 간 이동경로 조회 위임")
    void getRoute() {

        // given
        String origin = "해운대";
        String destination = "광안리";
        Transportation transportation = Transportation.CAR;

        KakaoRouteResult response =
                org.mockito.Mockito.mock(
                        KakaoRouteResult.class
                );

        when(kakaoMapServiceHandler.getRoute(
                origin,
                destination,
                transportation
        )).thenReturn(Mono.just(response));

        // when
        KakaoRouteResult result =
                tourismTool.getRoute(
                        origin,
                        destination,
                        transportation
                );

        // then
        assertEquals(response, result);

        verify(kakaoMapServiceHandler)
                .getRoute(
                        origin,
                        destination,
                        transportation
                );
    }

    @Test
    @DisplayName("음식점 상세정보 조회 위임")
    void getRestaurantDetail() {

        // given
        String contentId = "12345";

        Kor2RestaurantIntroResponse response =
                org.mockito.Mockito.mock(
                        Kor2RestaurantIntroResponse.class
                );

        when(kor2ServiceHandler.getRestaurantDetail(contentId))
                .thenReturn(Mono.just(response));

        // when
        Kor2RestaurantIntroResponse result =
                tourismTool.getRestaurantDetail(contentId);

        // then
        assertEquals(response, result);

        verify(kor2ServiceHandler)
                .getRestaurantDetail(contentId);
    }

    @Test
    @DisplayName("음식 영양정보 평가 위임 및 결정 가능한 태그 계산용 기록")
    void evaluateFoodNutrition() {

        // given
        String foodName = "비빔밥";
        DiseaseType diseaseType = DiseaseType.DIABETES;

        NutritionEvaluationResult response =
                new NutritionEvaluationResult(
                        diseaseType,
                        NutritionEvaluationStatus.AVAILABLE,
                        List.of(),
                        50.0,
                        500.0,
                        10.0
                );

        when(nutritionService.evaluateFoodNutrition(
                foodName,
                diseaseType
        )).thenReturn(Mono.just(response));

        // when
        NutritionEvaluationResult result =
                tourismTool.evaluateFoodNutrition(
                        foodName,
                        diseaseType
                );

        // then
        assertEquals(response, result);

        verify(nutritionService)
                .evaluateFoodNutrition(
                        foodName,
                        diseaseType
                );

        // 결정 가능한 RecommendationTag 계산에 재사용하기 위해 요청 단위로 기록되어야 한다
        verify(nutritionEvaluationCollector)
                .record(foodName, response);
    }
}
