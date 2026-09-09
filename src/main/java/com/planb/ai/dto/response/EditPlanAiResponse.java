package com.planb.ai.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

public record EditPlanAiResponse(
        @Schema(description = "수정된 일정 이름")
        String planName,
        @Schema(description = "수정 후 날짜별 일정")
        List<CreatePlanAiResponse.PlanDayDetail> planDays,
        @Schema(description = "사용자에게 표시할 주요 수정 사항 목록")
        List<String> changes,
        @Schema(description = "요청한 일정 수정을 적용할 수 있는지 여부")
        boolean processable
) {
}
