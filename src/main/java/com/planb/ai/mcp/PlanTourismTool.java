package com.planb.ai.mcp;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;
import java.util.Objects;

@RequiredArgsConstructor
public class PlanTourismTool {

    private final TourismTool tourismTool;

    private final PlaceCandidateContext candidates;
    private String attractionLocationDo;
    private String attractionLocationSigungu;
    private Kor2KeywordSearchResponse attractionResponse;

    public void resetCandidates() {

        candidates.clear();
        attractionLocationDo = null;
        attractionLocationSigungu = null;
        attractionResponse = null;
    }

    @Tool(description = """
            locationDo와 locationSigungu 범위의 실제 관광지 후보를 조회합니다.
            광역 지역은 시/도 전체, 도 지역은 시/군 범위로 Java가 조회합니다.
            keyword와 contentTypeId는 전달하지 않습니다.
            반환된 후보 중에서만 관광지를 선택하고 candidateId를 그대로 반환합니다.
            """)
    public List<PlaceCandidateContext.Candidate> searchAttractionsByRegion(
            String locationDo,
            String locationSigungu
    ) {

        if (attractionResponse != null
                && Objects.equals(attractionLocationDo, locationDo)
                && Objects.equals(attractionLocationSigungu, locationSigungu)) {
            return recordCandidates(attractionResponse);
        }

        attractionLocationDo = locationDo;
        attractionLocationSigungu = locationSigungu;
        attractionResponse = tourismTool
                .searchAttractionsByRegion(
                        locationDo,
                        locationSigungu
                );

        return recordCandidates(
                attractionResponse
        );
    }

    @Tool(description = """
            locationDo와 locationSigungu 범위에서 실제 음식점을 검색합니다.
            keyword에는 실제 음식명만 전달합니다.
            반환된 후보 중에서만 음식점을 선택하고 candidateId를 그대로 반환합니다.
            """)
    public List<PlaceCandidateContext.Candidate> searchRestaurantsByLocation(
            String keyword,
            String locationDo,
            String locationSigungu
    ) {

        return recordCandidates(
                tourismTool
                        .searchRestaurantsByLocation(
                                keyword,
                                locationDo,
                                locationSigungu
                        )
        );
    }

    private List<PlaceCandidateContext.Candidate> recordCandidates(
            Kor2KeywordSearchResponse response
    ) {

        if (response == null
                || response.response() == null
                || response.response().body() == null
                || response.response().body().items() == null
                || response.response().body().items().item() == null) {
            return List.of();
        }

        return response
                .response()
                .body()
                .items()
                .item()
                .stream()
                .filter(Objects::nonNull)
                .filter(item -> item.contentid() != null
                        && !item.contentid().isBlank())
                .map(candidates::record)
                .toList();
    }

    @Tool(description = """
            카카오에서 구체적 장소 후보와 경로 확인. keyword에는 지역을 포함한 실제 장소명을 전달합니다.
            '해운대구 관광지' 같은 일반 표현은 사용하지 않습니다.
            previousLocation은 직전 장소명이며 excludeNames는 제외할 장소명 목록입니다. ID는 넣지 않습니다.
            courseType에 맞는 카카오 카테고리만 검색하며 candidateId와 원본 categoryCode를 반환합니다.
            """)
    public PlaceWithRouteResult findPlaceWithRoute(
            String keyword,
            String previousLocation,
            Transportation transportation,
            List<String> excludeNames,
            CourseType courseType
    ) {

        String categoryCode = courseType == null
                ? null
                : switch (courseType) {
                    case CAFE_REST -> "CE7";
                    case ATTRACTION, MUST_HAVE, PARK_WALK -> "AT4";
                    default -> null;
                };

        if (categoryCode == null) {
            return new PlaceWithRouteResult(
                    false,
                    null,
                    null,
                    null,
                    null,
                    null
            );
        }

        PlaceWithRouteResult result = tourismTool
                .findPlaceWithRoute(
                        keyword,
                        previousLocation,
                        transportation,
                        excludeNames,
                        categoryCode
                );

        candidates.record(result);

        return result;
    }

    @Tool(description = """
            이번 호출의 검색 결과로 확정한 두 장소 사이의 이동시간 조회.
            originCandidateId와 destinationCandidateId에는 장소명이 아니라
            searchAttractionsByRegion, searchRestaurantsByLocation 또는 findPlaceWithRoute가 반환한 candidateId를 전달합니다.
            후보 ID가 없거나 이번 호출에서 검색하지 않은 후보이면 이동시간을 반환하지 않습니다.
            """)
    public KakaoRouteResult getRoute(
            String originCandidateId,
            String destinationCandidateId,
            Transportation transportation
    ) {

        PlaceCandidateContext.Candidate origin = candidates
                .find(originCandidateId);

        PlaceCandidateContext.Candidate destination = candidates
                .find(destinationCandidateId);

        if (origin == null || destination == null) {
            return new KakaoRouteResult(
                    originCandidateId,
                    destinationCandidateId,
                    null,
                    null);
        }

        return tourismTool
                .getRoute(
                        origin.name(),
                        destination.name(),
                        transportation,
                        origin.longitude(),
                        origin.latitude(),
                        destination.longitude(),
                        destination.latitude());
    }

    // AI에게 노출되는 식별자는 candidateId뿐이므로 접두사 제거를 프롬프트에 맡기지 않고 여기서 처리한다.
    @Tool(description = "TourAPI 음식점 상세 조회. 후보의 candidateId를 그대로 전달")
    public Kor2RestaurantIntroResponse getRestaurantDetail(String contentId) {

        return tourismTool.getRestaurantDetail(
                PlaceCandidateContext.contentId(contentId)
        );
    }

    @Tool(description = "질환별 음식 영양 평가")
    public NutritionEvaluationResult evaluateFoodNutrition(
            String foodName,
            DiseaseType diseaseType
    ) {

        return tourismTool.evaluateFoodNutrition(foodName, diseaseType);
    }
}
