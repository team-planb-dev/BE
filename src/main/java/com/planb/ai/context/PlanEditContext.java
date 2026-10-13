package com.planb.ai.context;

import com.planb.ai.dto.response.PlanEditScope;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;

import java.util.List;

public record PlanEditContext(
        CreateTravelRequest createTravelRequest,
        List<TravelHealthContext> healthContexts,
        GetAiPlanResponse currentPlan,
        String editRequest,
        List<EditTargetGuide> editTargets
) {

    public PlanEditContext(
            CreateTravelRequest createTravelRequest,
            List<TravelHealthContext> healthContexts,
            GetAiPlanResponse currentPlan,
            String editRequest
    ) {

        this(
                createTravelRequest,
                healthContexts,
                currentPlan,
                editRequest,
                List.of()
        );
    }

    public PlanEditContext withEditTargets(List<EditTargetGuide> editTargets) {

        return new PlanEditContext(
                createTravelRequest,
                healthContexts,
                currentPlan,
                editRequest,
                List.copyOf(editTargets)
        );
    }

    // Java가 확정한 부분 변경 대상, 메뉴만 변경 시 같은 음식점의 다른 메뉴 후보
    public record EditTargetGuide(
            Integer dayNumber,
            PlanEditScope.EditSlot slot,
            String placeName,
            boolean menuOnly,
            String sameRestaurantCandidateId,
            List<String> sameRestaurantMenus
    ) {
    }
}
