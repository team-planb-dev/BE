package com.planb.domain.travel.dto.response;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.entity.constant.RecommendationTag;

import java.util.List;
import java.util.Set;

// AI 일정 생성 API의 실제 응답 DTO
// CreatePlanAiResponse는 AI 구조화 응답 스키마 전용이라 최상위 tags를 두지 않음
// (AI에게 직접 채우게 하면 스케줄별 tags 취합값과 어긋날 수 있음)
// tags는 PlanService.aggregateTags()로 Java에서 집계한 값을 그대로 담는다
public record CreatePlanResponse(
        Set<RecommendationTag> tags,
        List<CreatePlanAiResponse.PlanDayDetail> planDays
) {

    public static CreatePlanResponse of(
            Set<RecommendationTag> tags,
            CreatePlanAiResponse createPlanAiResponse
    ) {

        return new CreatePlanResponse(
                tags,
                createPlanAiResponse.planDays()
        );
    }
}
