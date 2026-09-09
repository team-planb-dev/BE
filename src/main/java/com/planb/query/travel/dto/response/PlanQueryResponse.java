package com.planb.query.travel.dto.response;

import com.planb.domain.travel.entity.constant.RecommendationTag;

import java.util.Set;

public record PlanQueryResponse(
        Long planId,
        String planName,
        Set<RecommendationTag> tags
) {
}