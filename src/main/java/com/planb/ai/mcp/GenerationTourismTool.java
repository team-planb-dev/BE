package com.planb.ai.mcp;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.handler.GenerationRestaurantCandidates;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

/**
 * 생성 경로의 검색 Tool
 */
public class GenerationTourismTool {

    private final PlanTourismTool searchTool;

    private final GenerationRestaurantCandidates restaurants;

    public GenerationTourismTool(
            TourismTool tourismTool,
            PlaceCandidateContext candidates,
            List<Kor2KeywordSearchResponse.Item> plannedPlaces
    ) {

        this(
                tourismTool,
                candidates,
                plannedPlaces,
                null
        );
    }

    public GenerationTourismTool(
            TourismTool tourismTool,
            PlaceCandidateContext candidates,
            List<Kor2KeywordSearchResponse.Item> plannedPlaces,
            TravelPlanContext context
    ) {

        this.restaurants = new GenerationRestaurantCandidates(
                tourismTool,
                candidates,
                context == null ? List.of() : context.healthContexts(),
                context == null ? null : context
                        .createTravelRequest()
                        .locationDo(),
                context == null ? null : context
                        .createTravelRequest()
                        .locationSigungu()
        );
        candidates.generationRestaurants(restaurants);

        this.searchTool = new PlanTourismTool(
                tourismTool,
                candidates,
                plannedPlaces
        );
    }

    public void resetCandidates() {

        searchTool.resetCandidates();
        restaurants.clear();
    }

    @Tool(description = "여행 지역의 관광지 후보 검색. 반환된 candidateId와 좌표를 그대로 사용")
    public List<PlaceCandidateContext.Candidate> searchAttractionsByRegion(
            String locationDo,
            String locationSigungu
    ) {

        return searchTool.searchAttractionsByRegion(
                locationDo,
                locationSigungu
        );
    }

    @Tool(description = "여행 지역의 음식점 검색. keyword는 음식명만 전달")
    public List<GenerationRestaurantCandidates.RestaurantCandidate> searchRestaurantsByLocation(
            String keyword,
            String locationDo,
            String locationSigungu
    ) {

        return restaurants.search(
                keyword,
                locationDo,
                locationSigungu
        );
    }

    @Tool(description = "구체적 장소명으로 카페 또는 관광지 후보와 경로 확인. courseType과 제외 장소명 전달")
    public PlaceWithRouteResult findPlaceWithRoute(
            String keyword,
            String previousLocation,
            Transportation transportation,
            List<String> excludeNames,
            CourseType courseType
    ) {

        return searchTool.findPlaceWithRoute(
                keyword,
                previousLocation,
                transportation,
                excludeNames,
                courseType
        );
    }
}
