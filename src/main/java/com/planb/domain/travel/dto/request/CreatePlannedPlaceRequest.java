package com.planb.domain.travel.dto.request;

import com.planb.domain.travel.entity.Travel;

import java.util.List;

public record CreatePlannedPlaceRequest(
        Travel travel,
        List<PlannedPlaceDetail> plannedPlaceList) {

    public record PlannedPlaceDetail
            (String locationName,
             String location) {

        public static PlannedPlaceDetail from
                (CreateTravelRequest.PlannedPlaceDetail plannedPlace) {

            return new PlannedPlaceDetail(
                    plannedPlace
                            .locationName(),
                    plannedPlace
                            .location());
        }
    }

    public static CreatePlannedPlaceRequest from
            (Travel travel,
             CreateTravelRequest createTravelRequest) {

        List<PlannedPlaceDetail> plannedPlaceList = createTravelRequest
                .plannedPlaces()
                .stream()
                .map(PlannedPlaceDetail::from)
                .toList();

        return new CreatePlannedPlaceRequest(
                travel,
                plannedPlaceList);
    }
}