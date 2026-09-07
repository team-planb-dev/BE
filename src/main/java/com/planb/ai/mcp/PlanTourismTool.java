package com.planb.ai.mcp;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
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

    public void resetCandidates() {

        candidates.clear();
    }

    @Tool(description = """
            지역별 실제 장소 검색. 관광지 contentTypeId=12, 음식점=39.
            keyword에는 실제 장소명 또는 음식명만 전달하며 지역명·'관광지'·'명소'·'맛집'은 포함하지 않습니다.
            지역은 locationDo/locationSigungu로 별도 전달합니다.
            예: keyword='동백섬', locationDo='부산', locationSigungu='해운대구'.
            빈 결과이면 다른 구체적 장소명 후보를 검색하며 지역 전체의 후보 부재로 판단하지 않습니다.
            반환된 candidateId로 일정 장소를 선택합니다.
            """)
    public List<PlaceCandidateContext.Candidate> searchTourismByLocation(
            String keyword,
            String locationDo,
            String locationSigungu,
            Integer contentTypeId
    ) {

        Kor2KeywordSearchResponse response = tourismTool.searchTourismByLocation(
                keyword, locationDo, locationSigungu, contentTypeId);

        if (response == null || response.response() == null || response.response().body() == null
                || response.response().body().items() == null || response.response().body().items().item() == null) {
            return List.of();
        }

        return response.response().body().items().item().stream().filter(Objects::nonNull)
                .filter(item -> item.contentid() != null && !item.contentid().isBlank()).map(candidates::record).toList();
    }

    @Tool(description = """
            카카오에서 구체적 장소 후보와 경로 확인. keyword에는 지역을 포함한 실제 장소명을 전달합니다.
            '해운대구 관광지' 같은 일반 표현은 사용하지 않습니다.
            previousLocation은 직전 장소명이며 excludeNames는 제외할 장소명 목록입니다. ID는 넣지 않습니다.
            candidateId와 원본 categoryCode를 반환합니다. 관광지 AT4, 카페 CE7만 해당 용도로 선택합니다.
            """)
    public PlaceWithRouteResult findPlaceWithRoute(
            String keyword,
            String previousLocation,
            Transportation transportation,
            List<String> excludeNames
    ) {

        PlaceWithRouteResult result = tourismTool.findPlaceWithRoute(keyword, previousLocation, transportation, excludeNames);

        candidates.record(result);

        return result;
    }

    @Tool(description = """
            이번 호출의 검색 결과로 확정한 두 장소 사이의 이동시간 조회.
            originCandidateId와 destinationCandidateId에는 장소명이 아니라
            searchTourismByLocation 또는 findPlaceWithRoute가 반환한 candidateId를 전달합니다.
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

    @Tool(description = "TourAPI 음식점 상세 조회. tour: 접두사를 제외한 contentId 사용")
    public Kor2RestaurantIntroResponse getRestaurantDetail(String contentId) {

        return tourismTool.getRestaurantDetail(contentId);
    }

    @Tool(description = "질환별 음식 영양 평가")
    public NutritionEvaluationResult evaluateFoodNutrition(
            String foodName,
            DiseaseType diseaseType
    ) {

        return tourismTool.evaluateFoodNutrition(foodName, diseaseType);
    }
}
