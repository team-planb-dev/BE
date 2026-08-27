package com.planb.query.travel.dto.response;

import java.time.LocalDate;

public record PlanDayQueryResponse(Long planDayId,
                                   Integer dayNumber,
                                   LocalDate localdate) {
}
