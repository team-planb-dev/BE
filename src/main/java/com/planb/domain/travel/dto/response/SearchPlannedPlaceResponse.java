package com.planb.domain.travel.dto.response;

import java.util.List;

public record SearchPlannedPlaceResponse(List<PlannedPlaceDetail> plannedPlaces) {

    public record PlannedPlaceDetail(
            String locationName,
            String location) {
    }

}