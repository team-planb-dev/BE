package com.planb.unit.domain.travel.dto;

import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.entity.Travel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CreateTravelRequestSnapshotTest {

    @Test
    @DisplayName("편집 요청의 지역 음식 목록 독립 스냅샷")
    void detachedFoodLists() {

        List<String> localFoods = new ArrayList<>(List.of("황남빵"));
        List<String> recommendFoods = new ArrayList<>(List.of("쌈밥"));
        Travel travel = Travel
                .builder()
                .localFoods(localFoods)
                .recommendFoods(recommendFoods)
                .build();

        CreateTravelRequest request = CreateTravelRequest.from(
                travel,
                List.of(),
                List.of(1L)
        );

        localFoods.clear();
        recommendFoods.clear();

        assertEquals(List.of("황남빵"), request.localFoods());
        assertEquals(List.of("쌈밥"), request.recommendFoods());
    }
}
