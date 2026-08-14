package com.planb.domain.health.dto.request;

import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.constant.FoodType;

import java.util.List;

public record CreateFoodInfoRequest(Health health,
                                    List<FoodInfoDetail> data) {

    public record FoodInfoDetail(String foodName,
                                 FoodType foodType){

    }
}
