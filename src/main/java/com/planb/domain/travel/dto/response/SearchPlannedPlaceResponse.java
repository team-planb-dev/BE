package com.planb.domain.travel.dto.response;

import com.planb.domain.travel.entity.constant.PlaceType;

import java.util.List;

public record SearchPlannedPlaceResponse(List<PlannedPlaceDetail> plannedPlaces) {

    public record PlannedPlaceDetail(
            String locationName,
            String location,
            PlaceType placeType) {
    }

}