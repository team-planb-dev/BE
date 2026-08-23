package com.planb.global.client.foodNtrCpnt.dto.request;

public record FoodNtrCpntSearchRequest
        (String foodName,
         Integer pageNo,
         Integer numOfRows) {

    public static FoodNtrCpntSearchRequest of(String foodName) {

        return new FoodNtrCpntSearchRequest(
                foodName,
                1,
                10
        );
    }
}