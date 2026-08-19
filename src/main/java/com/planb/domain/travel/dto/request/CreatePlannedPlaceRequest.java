package com.planb.domain.travel.dto.request;

import com.planb.domain.travel.entity.Travel;

import java.util.List;

public record CreatePlannedPlaceRequest(Travel travel,
                                        List<PlannedPlaceDetail> plannedPlaceList) {

    public record PlannedPlaceDetail
            (String locationName,
             String location){
    }
}
