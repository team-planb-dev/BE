package com.planb.ai.dto.response;

public record PlaceWithRouteResult(
        boolean found,
        String placeName,
        String address,
        String longitude,
        String latitude,
        Integer travelMinutes,
        String candidateId,
        String categoryCode,
        String categoryName
) {
    public PlaceWithRouteResult(
            boolean found,
            String placeName,
            String address,
            String longitude,
            String latitude,
            Integer travelMinutes
    ) {

        this(found, placeName, address, longitude, latitude, travelMinutes, null, null, null);
    }
}
