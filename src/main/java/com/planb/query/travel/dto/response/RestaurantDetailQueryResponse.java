package com.planb.query.travel.dto.response;

public record RestaurantDetailQueryResponse(
        Long planScheduleId,
        String menuName,
        Double carbohydrate,
        Double sodium,
        Double fat,
        String openTime,
        String address,
        String longitude,
        String latitude,
        String imageUrl,
        String nutritionFoodName,
        String nutritionReferenceAmount,
        String nutritionSource
) {

    public RestaurantDetailQueryResponse(
            Long planScheduleId,
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

        this(
                planScheduleId,
                menuName,
                carbohydrate,
                sodium,
                fat,
                openTime,
                address,
                longitude,
                latitude,
                imageUrl,
                null,
                null,
                null
        );
    }
}
