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
    @DisplayName("지역 음식점 후보는 시군구 범위 조회에 위임")
    void searchRestaurantCandidatesByRegion() {

        Kor2KeywordSearchResponse response = mock(
                Kor2KeywordSearchResponse.class
        );

        when(
                kor2ServiceHandler
                        .searchRestaurantCandidates(
                                "강원특별자치도",
                                "춘천시"
                        )
        ).thenReturn(Mono.just(response));

        Kor2KeywordSearchResponse result = tourismTool
                .searchRestaurantCandidatesByRegion(
                        "강원특별자치도",
                        "춘천시"
                );

        assertEquals(response, result);

        verify(kor2ServiceHandler)
                .searchRestaurantCandidates(
                        "강원특별자치도",
                        "춘천시"
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
    @DisplayName("명확한 관광 분류 후보 유지")
    void keepsClearAttractionCategories() {

        List<Kor2KeywordSearchResponse.Item> source = List.of(
                attraction("1", "강릉향교", null, "HS01", "HS010900"),
                attraction("2", "대관령", null, "NA01", "NA010100"),
                attraction("3", "체험마을", null, "EX03", "EX030100"),
                attraction("4", "명주동골목", null, "VE04", "VE040100"),
                attraction("5", "경포해변", null, "NA02", "NA020900"),
                attraction("6", "주문진등대", null, "VE01", "VE010800"),
                attraction("7", "경포호수광장", null, "VE03", "VE030500"),
                attraction("8", "강릉시립미술관", null, "VE07", "VE070600")
        );

        when(
                kor2ServiceHandler
                        .searchAttractions(
                                "강원특별자치도",
                                "강릉시"
                        )
        ).thenReturn(Mono.just(response(source)));

        Set<String> selectedTitles = tourismTool
                .searchAttractionsByRegion(
                        "강원특별자치도",
                        "강릉시"
                )
                .response()
                .body()
                .items()
                .item()
                .stream()
                .map(Kor2KeywordSearchResponse.Item::title)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(
                source
                        .stream()
                        .map(Kor2KeywordSearchResponse.Item::title)
                        .collect(java.util.stream.Collectors.toSet()),
                selectedTitles
        );
    }

    @Test
    @DisplayName("혼합 및 미확인 분류 후보 제외")
    void excludesMixedAndUnknownAttractionCategories() {

        List<Kor2KeywordSearchResponse.Item> source = List.of(
                attraction(
                        "1",
                        "강릉교회",
                        null,
                        "HS03",
                        "HS030200"
                ),
                attraction(
                        "2",
                        "대한성공회 서울주교좌성당",
                        "https://example.com/cathedral.jpg",
                        "HS03",
                        "HS030200"
                ),
                attraction(
                        "3",
                        "강릉항여객터미널",
                        null,
                        "EX07",
                        "EX070100"
                ),
                attraction(
                        "4",
                        "강문해변화장실",
                        null,
                        "VE01",
                        "VE010100"
                ),
                attraction(
                        "5",
                        "기린사우나",
                        "https://example.com/sauna.jpg",
                        "EX05",
                        "EX050100"
                ),
                attraction(
                        "6",
                        "사천진항",
                        "https://example.com/harbor.jpg",
                        "NA02",
                        "NA020700"
                ),
                attraction(
                        "7",
                        "분류 미확인 장소",
                        "https://example.com/unknown.jpg",
                        null,
                        null
                ),
                attraction(
                        "8",
                        "구로기계공구단지",
                        "https://example.com/tool-complex.jpg",
                        "VE05",
                        "VE050100"
                ),
                attraction(
                        "9",
                        "강릉향교",
                        null,
                        "HS01",
                        "HS010900"
                ),
                attraction(
                        "10",
                        "경포해변",
                        "https://example.com/beach.jpg",
                        "NA02",
                        "NA020900"
                ),
                attraction(
                        "11",
                        "경포생태저류지",
                        "https://example.com/park.jpg",
                        "VE03",
                        "VE030500"
                )
        );

        when(
                kor2ServiceHandler
                        .searchAttractions(
                                "강원특별자치도",
                                "강릉시"
                        )
        ).thenReturn(Mono.just(response(source)));

        Set<String> selectedTitles = tourismTool
                .searchAttractionsByRegion(
                        "강원특별자치도",
                        "강릉시"
                )
                .response()
                .body()
                .items()
                .item()
                .stream()
                .map(Kor2KeywordSearchResponse.Item::title)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(
                Set.of(
                        "강릉향교",
                        "경포해변",
                        "경포생태저류지"
                ),
                selectedTitles
        );
    }

    @Test
    @DisplayName("기타문화시설 후보 전체 제외")
    void excludesOtherCulturalFacilityCandidates() {

        List<Kor2KeywordSearchResponse.Item> source = List.of(
                attraction(
                        "1",
                        "대한노인회 강릉시지회",
                        null,
                        "VE12",
                        "VE120300"
                ),
                attraction(
                        "2",
                        "임당생활문화센터",
                        "https://example.com/culture-center.jpg",
                        "VE12",
                        "VE120300"
                ),
                attraction(
                        "3",
                        "경포해변",
                        "https://example.com/beach.jpg",
                        "NA02",
                        "NA020900"
                )
        );

        when(
                kor2ServiceHandler
                        .searchAttractions(
                                "강원특별자치도",
                                "강릉시"
                        )
        ).thenReturn(Mono.just(response(source)));

        List<String> selectedTitles = tourismTool
                .searchAttractionsByRegion(
                        "강원특별자치도",
                        "강릉시"
                )
                .response()
                .body()
                .items()
                .item()
                .stream()
                .map(Kor2KeywordSearchResponse.Item::title)
                .toList();

        assertEquals(
                List.of("경포해변"),
                selectedTitles
        );
    }

    @Test
    @DisplayName("한국 범위를 벗어난 관광지 좌표 후보 제외")
    void excludesAttractionCandidatesOutsideKorea() {

        List<Kor2KeywordSearchResponse.Item> source = List.of(
                attraction(
                        "1",
                        "경포해변",
                        "128.9070",
                        "37.8050"
                ),
                attraction(
                        "2",
                        "대치유수지체육공원",
                        "117.9925662504",
                        "19.6944274800"
                )
        );

        when(
                kor2ServiceHandler
                        .searchAttractions(
                                "서울",
                                ""
                        )
        ).thenReturn(Mono.just(response(source)));

        List<String> selectedTitles = tourismTool
                .searchAttractionsByRegion(
                        "서울",
                        ""
                )
                .response()
                .body()
                .items()
                .item()
                .stream()
                .map(Kor2KeywordSearchResponse.Item::title)
                .toList();

        assertEquals(
                List.of("경포해변"),
                selectedTitles
        );
    }

    @Test
    @DisplayName("누락되거나 해석할 수 없는 관광지 좌표 후보 제외")
    void excludesAttractionCandidatesWithUnusableCoordinates() {

        List<Kor2KeywordSearchResponse.Item> source = List.of(
                attraction("1", "경포해변", "128.9070", "37.8050"),
                attraction("2", "경도 누락", null, "37.8050"),
                attraction("3", "위도 공백", "128.9070", ""),
                attraction("4", "경도 형식 오류", "invalid", "37.8050"),
                attraction("5", "비유한 경도", "NaN", "37.8050"),
                attraction("6", "비유한 위도", "128.9070", "Infinity")
        );

        when(
                kor2ServiceHandler
                        .searchAttractions(
                                "강원특별자치도",
                                "강릉시"
                        )
        ).thenReturn(Mono.just(response(source)));

        List<String> selectedTitles = tourismTool
                .searchAttractionsByRegion(
                        "강원특별자치도",
                        "강릉시"
                )
                .response()
                .body()
                .items()
                .item()
                .stream()
                .map(Kor2KeywordSearchResponse.Item::title)
                .toList();

        assertEquals(
                List.of("경포해변"),
                selectedTitles
        );
    }

    @Test
    @DisplayName("국내 도서 지역을 포함하는 관광지 좌표 범위 유지")
    void keepsAttractionCandidatesWithinKoreanCoordinateBounds() {

        List<Kor2KeywordSearchResponse.Item> source = List.of(
                attraction("1", "서쪽 경계", "124.0", "36.0"),
                attraction("2", "동쪽 경계", "132.0", "37.0"),
                attraction("3", "남쪽 경계", "126.0", "33.0"),
                attraction("4", "북쪽 경계", "128.0", "39.0"),
                attraction("5", "경도 범위 밖", "133.0", "37.0"),
                attraction("6", "위도 범위 밖", "128.0", "32.0"),
                attraction("7", "경위도 역전", "37.5", "127.0")
        );

        when(
                kor2ServiceHandler
                        .searchAttractions(
                                "제주특별자치도",
                                "제주시"
                        )
        ).thenReturn(Mono.just(response(source)));

        Set<String> selectedTitles = tourismTool
                .searchAttractionsByRegion(
                        "제주특별자치도",
                        "제주시"
                )
                .response()
                .body()
                .items()
                .item()
                .stream()
                .map(Kor2KeywordSearchResponse.Item::title)
                .collect(java.util.stream.Collectors.toSet());

        assertEquals(
                Set.of(
                        "서쪽 경계",
                        "동쪽 경계",
                        "남쪽 경계",
                        "북쪽 경계"
                ),
                selectedTitles
        );
    }

    @Test
    @DisplayName("품질 기준을 통과한 관광지가 없을 때 원본 후보 미복원")
    void doesNotRestoreRejectedAttractionCandidates() {

        when(
                kor2ServiceHandler
                        .searchAttractions(
                                "강원특별자치도",
                                "강릉시"
                        )
        ).thenReturn(
                Mono.just(
                        response(
                                List.of(
                                        attraction(
                                                "1",
                                                "강릉교회",
                                                null,
                                                "HS03",
                                                "HS030200"
                                        )
                                )
                        )
                )
        );

        List<Kor2KeywordSearchResponse.Item> selected = tourismTool
                .searchAttractionsByRegion(
                        "강원특별자치도",
                        "강릉시"
                )
                .response()
                .body()
                .items()
                .item();

        assertTrue(selected.isEmpty());
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
                                "CE7",
                                "129.2",
                                "35.8"
                        )
        ).thenReturn(Mono.just(response));

        assertEquals(
                response,
                tourismTool.findPlaceWithRoute(
                        "경주 카페 황남다락",
                        "첨성대",
                        Transportation.TRANSIT,
                        List.of(),
                        "CE7",
                        "129.2",
                        "35.8"
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
        List<DiseaseType> diseaseTypes =
                List.of(DiseaseType.DIABETES);

        NutritionEvaluationResult response =
                new NutritionEvaluationResult(
                        diseaseTypes,
                        NutritionEvaluationStatus.AVAILABLE,
                        List.of(),
                        50.0,
                        500.0,
                        10.0
                );

        when(nutritionService.evaluateFoodNutrition(
                foodName,
                foodName,
                diseaseTypes
        )).thenReturn(Mono.just(response));

        // when
        NutritionEvaluationResult result =
                tourismTool.evaluateFoodNutrition(
                        foodName,
                        foodName,
                        diseaseTypes
                );

        // then
        assertEquals(response, result);

        verify(nutritionService)
                .evaluateFoodNutrition(
                        foodName,
                        foodName,
                        diseaseTypes
                );

        // 결정 가능한 RecommendationTag 계산 재사용을 위한 요청 단위 기록
        verify(nutritionEvaluationCollector)
                .record(foodName, response);
    }

    @Test
    @DisplayName("표준 품목명을 조회에 넘기되 기록은 메뉴명으로 남김")
    void evaluateFoodNutritionWithStandardName() {

        // given
        String foodName = "검은콩 장칼국수";
        String standardFoodName = "칼국수";
        List<DiseaseType> diseaseTypes =
                List.of(DiseaseType.DIABETES);

        NutritionEvaluationResult response =
                new NutritionEvaluationResult(
                        diseaseTypes,
                        NutritionEvaluationStatus.AVAILABLE,
                        List.of(),
                        50.0,
                        500.0,
                        10.0
                );

        when(nutritionService.evaluateFoodNutrition(
                foodName,
                standardFoodName,
                diseaseTypes
        )).thenReturn(Mono.just(response));

        // when
        NutritionEvaluationResult result =
                tourismTool.evaluateFoodNutrition(
                        foodName,
                        standardFoodName,
                        diseaseTypes
                );

        // then
        assertEquals(response, result);

        // 수치를 되찾는 쪽은 restaurantDetail.menuName()을 키로 쓴다.
        // 여기에 표준 품목명을 넣으면 조회는 성공하는데 화면은 빈칸이 된다.
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

        return attraction(
                contentId,
                "관광지 " + contentId,
                "https://example.com/attraction.jpg",
                "NA02",
                "NA020900"
        );
    }

    private Kor2KeywordSearchResponse.Item attraction(
            String contentId,
            String title,
            String firstImage,
            String categoryLevel2,
            String categoryLevel3
    ) {

        return new Kor2KeywordSearchResponse.Item(
                "주소",
                "",
                null,
                contentId,
                "12",
                null,
                firstImage,
                null,
                null,
                "128.9",
                "37.7",
                null,
                null,
                null,
                title,
                "51",
                "150",
                categoryLevel2 == null
                        ? null
                        : categoryLevel2.substring(0, 2),
                categoryLevel2,
                categoryLevel3
        );
    }

    private Kor2KeywordSearchResponse.Item attraction(
            String contentId,
            String title,
            String longitude,
            String latitude
    ) {

        return new Kor2KeywordSearchResponse.Item(
                "주소",
                "",
                null,
                contentId,
                "12",
                null,
                "https://example.com/attraction.jpg",
                null,
                null,
                longitude,
                latitude,
                null,
                null,
                null,
                title,
                "11",
                "680",
                "VE",
                "VE03",
                "VE030500"
        );
    }

}
