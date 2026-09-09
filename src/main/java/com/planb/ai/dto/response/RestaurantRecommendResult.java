package com.planb.ai.dto.response;

public record RestaurantRecommendResult(
        boolean found,
        String locationName,
        String location,
        RestaurantDetailResult restaurantDetail,
        Integer travelMinutes
) {
    public record RestaurantDetailResult(
            String menuName,
            Double carbohydrate,
            Double sodium,
            Double fat,
            String openTime,
            String address,
            String longitude,
            String latitude,
            String imageUrl
    ) {
    }
}
