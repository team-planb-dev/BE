package com.planb.ai.prompt;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.entity.constant.Transportation;

import java.util.List;

public record RestaurantRecommendPrompt(
        String locationDo,
        String locationSigungu,
        String previousLocation,
        Transportation transportation,
        List<String> localFoods,
        List<String> recommendFoods,
        List<DiseaseType> diseaseTypes,
        List<String> excludeMenuNames
) implements AiPrompt {

    @Override
    public String system() {
        return """
                너는 여행 일정 중 음식점(RESTAURANT) 슬롯 하나를 새로 확정하는 AI다.
                excludeMenuNames에 있는 메뉴와 겹치지 않는 실제 음식점을 확정해야 한다.

                - searchTourismByLocation(keyword, locationDo, locationSigungu, contentTypeId=39)로
                  실제 음식점을 검색한다. keyword는 localFoods, recommendFoods를 우선 후보로 사용한다.
                - 검색 결과의 contentId로 getRestaurantDetail을 호출해 대표메뉴
                  (firstmenu 우선, 없으면 treatmenu)를 확인한다.
                - 확인한 메뉴명이 excludeMenuNames에 있으면 다른 검색 결과 item이나 keyword로
                  최대 1회 재시도한다. 재시도해도 겹치지 않는 메뉴를 찾지 못하면
                  found를 false로 반환하고 나머지 필드는 모두 null로 둔다.
                - 메뉴가 확정되면 그 메뉴명으로 diseaseTypes 각각에 대해
                  evaluateFoodNutrition을 호출한다.
                - locationName, location, restaurantDetail의 menuName, openTime, address,
                  longitude, latitude, imageUrl은 반드시 같은 하나의 검색결과 item /
                  getRestaurantDetail 결과에서만 가져온다. carbohydrate, sodium, fat은
                  evaluateFoodNutrition 결과를 그대로 사용하고, 값이 없으면 null로 둔다.
                  Tool 결과에 없는 값을 임의로 생성하지 않는다.
                - previousLocation이 빈 문자열이 아니면 확정한 음식점의 실제 장소명으로
                  getRoute(previousLocation, 장소명, transportation)를 호출해 travelMinutes를
                  구한다. previousLocation이 빈 문자열이면 getRoute를 호출하지 않고
                  travelMinutes를 null로 둔다.
                - Tool을 호출하지 않고 임의로 응답을 생성하지 않는다.
                """;
    }

    @Override
    public String user() {
        return """
                locationDo: %s
                locationSigungu: %s
                previousLocation: %s
                transportation: %s
                localFoods: %s
                recommendFoods: %s
                diseaseTypes: %s
                excludeMenuNames: %s
                """.formatted(
                locationDo,
                locationSigungu,
                previousLocation,
                transportation,
                localFoods,
                recommendFoods,
                diseaseTypes,
                excludeMenuNames
        );
    }
}
