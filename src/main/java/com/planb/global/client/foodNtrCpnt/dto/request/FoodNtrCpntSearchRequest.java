package com.planb.global.client.foodNtrCpnt.dto.request;

public record FoodNtrCpntSearchRequest
        (String foodName,
         String dbClassName,
         Integer pageNo,
         Integer numOfRows) {

    public static FoodNtrCpntSearchRequest of(String foodName) {

        return new FoodNtrCpntSearchRequest(
                foodName,
                "품목대표",
                1,
                100
        );
    }
}