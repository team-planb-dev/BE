package com.planb.query.travel.dto.response;

import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;

public record TravelConditionQueryResponse(TravelStyle travelStyle,
                                           TravelTheme travelTheme) {
}
