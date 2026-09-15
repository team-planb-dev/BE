package com.planb.query.travel.dto.response;

import com.planb.domain.travel.entity.constant.TravelTheme;

import java.time.LocalDate;

public record TravelListItemQueryResponse(
        Long travelId,
        String travelName,
        String locationDo,
        String locationSigungu,
        LocalDate startDate,
        LocalDate endDate,
        TravelTheme travelTheme
) {
}
