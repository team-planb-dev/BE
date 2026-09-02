// src/main/java/com/planb/ai/mcp/TourismTool.java
package com.planb.ai.mcp;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.service.NutritionService;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TourismTool {

    private static final List<String> ZONE_TITLE_KEYWORDS = List.of("관광특구", "지구", "권역");

    private final Kor2ServiceHandler kor2ServiceHandler;
    private final KakaoMapServiceHandler kakaoMapServiceHandler;
    private final NutritionService nutritionService;
    private final NutritionEvaluationCollector nutritionEvaluationCollector;

    @Tool(description = """
        여행 일정 생성 중 실제 관광지 또는 음식점을 지역 조건과 함께 검색합니다.

        keyword에는 지역명이나 '맛집' 같은 표현을 포함하지 않고,
        실제 검색할 장소명 또는 음식명만 전달합니다.

        locationDo와 locationSigungu에는
        여행 요청에 포함된 시/도와 시/군/구 값을 그대로 사용합니다.

        contentTypeId에는 검색하려는 관광정보 유형의 코드를 전달합니다.
        """)
    public Kor2KeywordSearchResponse searchTourismByLocation(
            String keyword,
            String locationDo,
            String locationSigungu,
            Integer contentTypeId
    ) {

        log.info(
                "[AI TOOL] 지역 기반 관광정보 검색 호출 - keyword: {}, locationDo: {}, locationSigungu: {}, contentTypeId: {}",
                keyword,
                locationDo,
                locationSigungu,
                contentTypeId
        );

        return kor2ServiceHandler
                .searchKeyword(
                        keyword,
                        locationDo,
                        locationSigungu,
                        contentTypeId
                )
                .map(response -> excludeZoneItemsIfPossible(response, contentTypeId))
                .doOnNext(response ->
                        log.info(
                                "[AI TOOL] 지역 기반 관광정보 검색 응답 - {}",
                                response
                        )
                )
                .block();
    }

    private Kor2KeywordSearchResponse excludeZoneItemsIfPossible(
            Kor2KeywordSearchResponse response,
            Integer contentTypeId
    ) {
        if (!Integer.valueOf(12).equals(contentTypeId)) {
            return response;
        }

        List<Kor2KeywordSearchResponse.Item> items = response.response().body().items().item();
        if (items.isEmpty()) {
            return response;
        }

        List<Kor2KeywordSearchResponse.Item> filtered = items.stream()
                .filter(item -> !isZoneTitle(item.title()))
                .toList();

        List<Kor2KeywordSearchResponse.Item> resultItems = filtered.isEmpty() ? items : filtered;

        return new Kor2KeywordSearchResponse(
                new Kor2KeywordSearchResponse.Response(
                        response.response().header(),
                        new Kor2KeywordSearchResponse.Body(
                                new Kor2KeywordSearchResponse.Items(resultItems),
                                response.response().body().numOfRows(),
                                response.response().body().pageNo(),
                                response.response().body().totalCount()
                        )
                )
        );
    }

    private boolean isZoneTitle(String title) {
        return title != null
                && ZONE_TITLE_KEYWORDS.stream().anyMatch(title::contains);
    }

    @Tool(description = """
            두 장소 사이의 실제 이동거리와 예상 이동시간을 조회합니다.
            출발지와 도착지의 장소명을 기준으로 좌표를 검색한 뒤,
            사용자의 이동수단에 맞는 실제 경로를 조회합니다.

            TRANSIT은 버스 및 지하철을 포함한 대중교통을 의미합니다.
            CAR는 자가용 이동을 의미합니다.
            """)
    public KakaoRouteResult getRoute(
            String origin,
            String destination,
            Transportation transportation
    ) {

        log.info(
                "[AI TOOL] 이동경로 조회 호출 - origin: {}, destination: {}, transportation: {}",
                origin,
                destination,
                transportation
        );

        return kakaoMapServiceHandler.getRoute(
                origin,
                destination,
                transportation
        ).block();
    }

    @Tool(description = """
        음식점의 실제 대표메뉴, 취급메뉴, 영업정보를 조회합니다.
        RESTAURANT 일정을 생성할 때는 반드시
        searchTourism으로 얻은 contentId를 사용해 이 Tool을 호출하세요.
        """)
    public Kor2RestaurantIntroResponse getRestaurantDetail(
            String contentId
    ) {

        log.info(
                "[AI TOOL] 음식점 상세정보 호출 - contentId: {}",
                contentId
        );

        return kor2ServiceHandler
                .getRestaurantDetail(contentId)
                .block();
    }

    @Tool(description = """
        음식 이름을 기준으로 식품 영양정보를 조회하고,
        여행자의 질환에 필요한 영양성분을 평가합니다.

        영양정보가 존재하는 경우 질환별 기준에 따라
        각 영양성분을 LOW, CHECK, HIGH로 평가합니다.

        영양정보를 조회할 수 없거나 평가에 필요한 영양성분이 부족한 경우
        반환되는 평가 상태를 확인하고 영양정보를 임의로 추정하지 마세요.

        음식 추천 시 사용자의 질환에 적합한 메뉴인지 판단하는
        근거가 필요한 경우 사용합니다.
        """)
    public NutritionEvaluationResult evaluateFoodNutrition(
            String foodName,
            DiseaseType diseaseType
    ) {

        log.info(
                "[AI TOOL] 영양정보 평가 호출 - foodName: {}, diseaseType: {}",
                foodName,
                diseaseType
        );

        NutritionEvaluationResult result =
                nutritionService.evaluateFoodNutrition(
                        foodName,
                        diseaseType
                ).block();

        // 결정 가능한 RecommendationTag 계산에 재사용하기 위해 요청 단위로 기록
        nutritionEvaluationCollector.record(foodName, result);

        return result;
    }

    @Tool(description = """
        실제 장소(카페 또는 TourAPI에서 검색되지 않는 관광지)의 존재 여부를
        카카오맵에서 확인하고, 동시에 바로 이전 일정 장소로부터
        이 장소까지의 실제 이동시간을 함께 조회합니다.

        다음 두 가지 경우에 사용합니다.
        1) CAFE_REST(카페·휴식) 일정 후보로 제안한 카페 상호명 확인
        2) searchTourismByLocation(contentTypeId=12)으로 검색되지 않는
           관광지 후보(plannedPlaces 등)의 최후 대체 확인

        keyword에는 지역명을 포함한 실제 장소명 후보를 전달합니다.
        (예: "해운대 스타벅스", "해운대해수욕장")
        지역명 없이 "카페"처럼 너무 일반적인 keyword만 전달하지 않습니다.

        previousLocation에는 바로 이전 일정의 실제 장소명을 전달합니다.
        이전 일정이 없거나 이동시간이 필요 없으면 빈 문자열을 전달합니다.

        excludeNames에는 이 여행에서 지금까지 확정한 모든 실제 장소명
        (ATTRACTION locationName, CAFE_REST locationName)을 전달합니다.
        검색 결과가 이 목록에 있는 이름과 같으면 이미 사용된 장소로 간주되어
        found가 false로 반환됩니다. 사용한 이름이 없으면 빈 배열을 전달합니다.

        found가 false이면 해당 후보는 존재하지 않거나 이미 사용된 것이므로,
        다른 후보명으로 다시 호출하거나 대체 처리합니다.
        """)
    public PlaceWithRouteResult findPlaceWithRoute(
            String keyword,
            String previousLocation,
            Transportation transportation,
            List<String> excludeNames
    ) {

        log.info(
                "[AI TOOL] 장소+경로 조회 호출 - keyword: {}, previousLocation: {}, transportation: {}, excludeNames: {}",
                keyword,
                previousLocation,
                transportation,
                excludeNames
        );

        return kakaoMapServiceHandler
                .findPlaceWithRoute(keyword, previousLocation, transportation, excludeNames)
                .block();
    }
}
