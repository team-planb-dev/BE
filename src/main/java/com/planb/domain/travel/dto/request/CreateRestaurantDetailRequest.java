package com.planb.domain.travel.dto.request;

import com.planb.ai.dto.response.CreatePlanAiResponse;

public record CreateRestaurantDetailRequest(
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

    public static CreateRestaurantDetailRequest from(
            CreatePlanAiResponse.RestaurantDetail restaurantDetail
    ) {

        return new CreateRestaurantDetailRequest(
                restaurantDetail.menuName(),
                restaurantDetail.carbohydrate(),
                restaurantDetail.sodium(),
                restaurantDetail.fat(),
                restaurantDetail.openTime(),
                restaurantDetail.address(),
                restaurantDetail.longitude(),
                restaurantDetail.latitude(),
                restaurantDetail.imageUrl()
        );
    }
}