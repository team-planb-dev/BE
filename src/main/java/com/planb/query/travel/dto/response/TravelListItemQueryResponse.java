package com.planb.query.travel.dto.response;

import java.time.LocalDate;

public record TravelListItemQueryResponse(
        Long travelId,
        String travelName,
        String locationDo,
        String locationSigungu,
        LocalDate startDate,
        LocalDate endDate
) {
}
