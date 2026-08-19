package com.planb.domain.travel.dto.request;

import com.planb.domain.travel.entity.Travel;

public record CreatePlanRequest(Travel travel,String planName) {
}
