package com.planb.ai.dto.response;

public record PlaceWithRouteResult(
        boolean found,
        String placeName,
        String address,
        String longitude,
        String latitude,
        Integer travelMinutes
) {
}