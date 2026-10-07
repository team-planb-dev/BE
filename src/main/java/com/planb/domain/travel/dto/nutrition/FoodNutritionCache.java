package com.planb.domain.travel.dto.nutrition;

import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;

import java.util.List;

/**
 * 메뉴명별 식약처 영양 조회 원본 결과의 Redis 저장 형식
 */
public record FoodNutritionCache(
        List<FoodNtrCpntResponse.Item> items
) {
}
