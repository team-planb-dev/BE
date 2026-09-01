package com.planb.domain.travel.dto.request;

import com.planb.domain.travel.entity.Plan;

import java.time.LocalDate;

public record CreatePlanDayRequest(Plan plan,
                                   Integer dayNumber,
                                   LocalDate planDate) {
}
