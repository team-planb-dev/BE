package com.planb.domain.travel.dto.request;

public record MakeRecommendFoodsRequest(String locationDo,
                                        String locationSigungu) {

    public String fullLocation(){
        return locationDo+ " " + locationSigungu;
    }
}
