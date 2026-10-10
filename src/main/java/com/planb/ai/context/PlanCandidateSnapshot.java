package com.planb.ai.context;

import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse.Item;

import java.util.List;

/**
 * 생성 전 조회한 장소 후보 원본
 */
public record PlanCandidateSnapshot(
        List<Item> attractions,
        List<Item> restaurants,
        List<Item> plannedPlaces,
        boolean regionalRestaurantsCollected
) {

    public PlanCandidateSnapshot {

        attractions = List.copyOf(attractions);
        restaurants = List.copyOf(restaurants);
        plannedPlaces = List.copyOf(plannedPlaces);
    }

    public void restore(PlaceCandidateContext candidates) {

        candidates.clear();
        attractions.forEach(candidates::record);
        restaurants.forEach(candidates::record);
        plannedPlaces.forEach(candidates::pin);
        candidates.markPrefetched(regionalRestaurantsCollected);
    }
}
