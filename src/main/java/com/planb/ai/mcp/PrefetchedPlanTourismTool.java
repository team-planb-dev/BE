package com.planb.ai.mcp;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import org.springframework.ai.tool.annotation.Tool;

import java.util.List;

/**
 * 선조회 후보를 사용하는 생성 전용 Tool
 */
public class PrefetchedPlanTourismTool {

    private final PlanTourismTool delegate;

    private final PlaceCandidateContext candidates;

    private final PlaceCandidateContext seed = new PlaceCandidateContext();

    public PrefetchedPlanTourismTool(
            TourismTool tourismTool,
            PlaceCandidateContext candidates
    ) {

        this.delegate = new PlanTourismTool(tourismTool, candidates);
        this.candidates = candidates;
        seed.copyFrom(candidates);
    }

    public void resetCandidates() {

        candidates.copyFrom(seed);
    }

    @Tool(description = "구체적인 카페·대체 관광지 확인. 지역과 실제 장소명, 직전 장소명과 제외 장소명 사용")
    public PlaceWithRouteResult findPlaceWithRoute(
            String keyword,
            String previousLocation,
            Transportation transportation,
            List<String> excludeNames,
            CourseType courseType
    ) {

        return delegate
                .findPlaceWithRoute(
                        keyword,
                        previousLocation,
                        transportation,
                        excludeNames,
                        courseType
                );
    }

    @Tool(description = "선조회 또는 이번 Tool이 확인한 두 candidateId 사이의 실제 이동시간 조회")
    public KakaoRouteResult getRoute(
            String originCandidateId,
            String destinationCandidateId,
            Transportation transportation
    ) {

        return delegate
                .getRoute(
                        originCandidateId,
                        destinationCandidateId,
                        transportation
                );
    }

    @Tool(description = "선조회 TourAPI 음식점 candidateId 또는 원본 contentId의 실제 메뉴 상세 조회")
    public Kor2RestaurantIntroResponse getRestaurantDetail(String contentId) {

        var candidate = candidates.find("tour:" + PlaceCandidateContext.contentId(contentId));

        if (candidate == null || !"39".equals(candidate.type())) {
            return null;
        }

        return delegate
                .getRestaurantDetail(contentId);
    }

    @Tool(description = "실제 음식과 표준 품목명을 사용하는 질환별 영양 평가")
    public NutritionEvaluationResult evaluateFoodNutrition(
            String foodName,
            String standardFoodName,
            List<DiseaseType> diseaseTypes
    ) {

        return delegate
                .evaluateFoodNutrition(
                        foodName,
                        standardFoodName,
                        diseaseTypes
                );
    }
}
