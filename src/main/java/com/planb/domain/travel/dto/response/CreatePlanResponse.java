package com.planb.domain.travel.dto.response;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.Set;

// AI 일정 생성 API의 실제 응답 DTO
// CreatePlanAiResponse는 AI 구조화 응답 스키마 전용이라 최상위 tags를 두지 않음
// (AI에게 직접 채우게 하면 스케줄별 tags 취합값과 어긋날 수 있음)
// tags는 PlanService.aggregateTags()로 Java에서 집계한 값 그대로 사용
// travelId와 saved는 저장 확정과 공유 가능 여부를 프런트가 판단하기 위한 값
public record CreatePlanResponse(
        @Schema(description = "생성된 여행 ID", example = "1")
        Long travelId,

        @Schema(description = "사용자가 저장을 확정했는지 여부입니다. 생성 직후에는 false입니다.", example = "false")
        boolean saved,

        @Schema(description = "전체 일정에 적용된 추천 근거 태그")
        Set<RecommendationTag> tags,

        @Schema(description = "날짜별 생성 일정")
        List<CreatePlanAiResponse.PlanDayDetail> planDays
) {

    public static CreatePlanResponse of(
            Travel travel,
            Set<RecommendationTag> tags,
            CreatePlanAiResponse createPlanAiResponse
    ) {

        return new CreatePlanResponse(
                travel
                        .getId(),
                travel
                        .isSaved(),
                tags,
                createPlanAiResponse.planDays()
        );
    }
}
