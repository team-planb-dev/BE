package com.planb.unit.global.ai.tool;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
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
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
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
    @DisplayName("음식점은 시군구 기반 키워드 검색에 위임")
    void searchRestaurantsByLocation() {

        Kor2KeywordSearchResponse response =
                org.mockito.Mockito.mock(
                        Kor2KeywordSearchResponse.class
                );

        when(
                kor2ServiceHandler
                        .searchRestaurants(
                                "돼지국밥",
                                "부산",
                                "해운대구"
                        )
        ).thenReturn(Mono.just(response));

        Kor2KeywordSearchResponse result =
                tourismTool
                        .searchRestaurantsByLocation(
                                "돼지국밥",
                                "부산",
                                "해운대구"
                        );

        assertEquals(response, result);

        verify(kor2ServiceHandler)
                .searchRestaurants(
                        "돼지국밥",
                        "부산",
                        "해운대구"
                );
    }

    @Test
    @DisplayName("지역 관광지 후보는 원본 후보에서 최대 40개만 반환")
    void selectsAttractionCandidatesWithinLimit() {

        List<Kor2KeywordSearchResponse.Item> source = IntStream
                .range(0, 50)
                .mapToObj(index ->
                        attraction(String.valueOf(index))
                )
                .toList();

        when(
                kor2ServiceHandler
                        .searchAttractions(
                                "서울",
                                "종로구"
                        )
        )
                .thenReturn(Mono.just(response(source)));

        List<Kor2KeywordSearchResponse.Item> selected = tourismTool
                .searchAttractionsByRegion(
                        "서울",
                        "종로구"
                )
                .response()
                .body()
                .items()
                .item();

        assertEquals(40, selected.size());
        assertTrue(source.containsAll(selected));
        verify(kor2ServiceHandler)
                .searchAttractions(
                        "서울",
                        "종로구"
                );
    }

    @Test
    @DisplayName("지역 관광지 후보가 제한보다 적으면 모두 반환")
    void keepsAllAttractionCandidatesWhenInsufficient() {

        List<Kor2KeywordSearchResponse.Item> source = List.of(
                attraction("1"),
                attraction("2")
        );

        when(
                kor2ServiceHandler
                        .searchAttractions(
                                "서울",
                                "종로구"
                        )
        )
                .thenReturn(Mono.just(response(source)));

        List<Kor2KeywordSearchResponse.Item> selected = tourismTool
                .searchAttractionsByRegion(
                        "서울",
                        "종로구"
                )
                .response()
                .body()
                .items()
                .item();

        assertEquals(Set.copyOf(source), Set.copyOf(selected));
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
    @DisplayName("장소 검색 시 요청 카테고리 전달")
    void findPlaceWithRoute() {

        PlaceWithRouteResult response = new PlaceWithRouteResult(
                true,
                "카페 황남다락",
                "경주시 황남동",
                "129.2",
                "35.8",
                10,
                "kakao:cafe",
                "CE7",
                "음식점 > 카페"
        );

        when(
                kakaoMapServiceHandler
                        .findPlaceWithRoute(
                                "경주 카페 황남다락",
                                "첨성대",
                                Transportation.TRANSIT,
                                List.of(),
                                "CE7"
                        )
        ).thenReturn(Mono.just(response));

        assertEquals(
                response,
                tourismTool.findPlaceWithRoute(
                        "경주 카페 황남다락",
                        "첨성대",
                        Transportation.TRANSIT,
                        List.of(),
                        "CE7"
                )
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

        // 결정 가능한 RecommendationTag 계산 재사용을 위한 요청 단위 기록
        verify(nutritionEvaluationCollector)
                .record(foodName, response);
    }

    private Kor2KeywordSearchResponse response(
            List<Kor2KeywordSearchResponse.Item> items
    ) {

        return new Kor2KeywordSearchResponse(
                new Kor2KeywordSearchResponse.Response(
                        null,
                        new Kor2KeywordSearchResponse.Body(
                                new Kor2KeywordSearchResponse.Items(items),
                                items.size(),
                                1,
                                items.size()
                        )
                )
        );
    }

    private Kor2KeywordSearchResponse.Item attraction(
            String contentId
    ) {

        Kor2KeywordSearchResponse.Item item = mock(
                Kor2KeywordSearchResponse.Item.class
        );

        when(item.title())
                .thenReturn("관광지 " + contentId);

        return item;
    }

}
