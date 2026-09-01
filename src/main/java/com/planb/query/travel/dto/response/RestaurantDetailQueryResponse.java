package com.planb.query.travel.dto.response;

public record RestaurantDetailQueryResponse(Long planScheduleId,
                                            String menuName,
                                            Double carbohydrate,
                                            Double sodium,
                                            Double fat,
                                            String openTime,
                                            String address,
                                            String longitude,
                                            String latitude,
                                            String imageUrl) {
}
