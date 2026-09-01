package com.planb.unit.global.client.foodNtrCpnt;

import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.client.foodNtrCpnt.helper.FoodNtrCpntHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoodNtrCpntHelperTest {

    private final FoodNtrCpntHelper foodNtrCpntHelper =
            new FoodNtrCpntHelper();

    @Test
    @DisplayName("외식 음식 영양정보 우선 필터링")
    void filterOutsideFoodNutrition() {

        FoodNtrCpntResponse.Item outsideFood =
                makeItem(
                        "돼지국밥",
                        "음식",
                        "외식_부산"
                );

        FoodNtrCpntResponse.Item generalFood =
                makeItem(
                        "돼지국밥",
                        "음식",
                        "가정식"
                );

        FoodNtrCpntResponse response =
                makeResponse(
                        List.of(
                                generalFood,
                                outsideFood
                        )
                );

        List<FoodNtrCpntResponse.Item> result =
                foodNtrCpntHelper.filterFoodNutrition(
                        response,
                        "돼지국밥"
                );

        assertEquals(
                1,
                result.size()
        );

        assertEquals(
                outsideFood,
                result.getFirst()
        );
    }

    @Test
    @DisplayName("급식 음식 영양정보 제외")
    void excludeSchoolMealNutrition() {

        FoodNtrCpntResponse.Item schoolMeal =
                makeItem(
                        "돼지국밥",
                        "음식",
                        "학교급식"
                );

        FoodNtrCpntResponse response =
                makeResponse(
                        List.of(schoolMeal)
                );

        List<FoodNtrCpntResponse.Item> result =
                foodNtrCpntHelper.filterFoodNutrition(
                        response,
                        "돼지국밥"
                );

        assertTrue(
                result.isEmpty()
        );
    }

    @Test
    @DisplayName("음식 DB 그룹 외 영양정보 제외")
    void excludeNonFoodNutrition() {

        FoodNtrCpntResponse.Item item =
                makeItem(
                        "돼지국밥",
                        "가공식품",
                        "외식_부산"
                );

        FoodNtrCpntResponse response =
                makeResponse(
                        List.of(item)
                );

        List<FoodNtrCpntResponse.Item> result =
                foodNtrCpntHelper.filterFoodNutrition(
                        response,
                        "돼지국밥"
                );

        assertTrue(
                result.isEmpty()
        );
    }

    @Test
    @DisplayName("정규화된 식품명 정확 일치")
    void filterExactFoodName() {

        FoodNtrCpntResponse.Item exactFood =
                makeItem(
                        "부산_돼지국밥",
                        "음식",
                        "외식_부산"
                );

        FoodNtrCpntResponse.Item similarFood =
                makeItem(
                        "돼지국밥_특",
                        "음식",
                        "외식_부산"
                );

        FoodNtrCpntResponse response =
                makeResponse(
                        List.of(
                                exactFood,
                                similarFood
                        )
                );

        List<FoodNtrCpntResponse.Item> result =
                foodNtrCpntHelper.filterFoodNutrition(
                        response,
                        "돼지국밥"
                );

        assertEquals(
                1,
                result.size()
        );

        assertEquals(
                exactFood,
                result.getFirst()
        );
    }

    @Test
    @DisplayName("부분 일치 식품명 검색")
    void filterContainsFoodName() {

        FoodNtrCpntResponse.Item item =
                makeItem(
                        "얼큰돼지국밥",
                        "음식",
                        "외식_부산"
                );

        FoodNtrCpntResponse response =
                makeResponse(
                        List.of(item)
                );

        List<FoodNtrCpntResponse.Item> result =
                foodNtrCpntHelper.filterFoodNutrition(
                        response,
                        "돼지국밥"
                );

        assertEquals(
                1,
                result.size()
        );

        assertEquals(
                item,
                result.getFirst()
        );
    }

    @Test
    @DisplayName("영양정보 검색 결과 최대 3개 제한")
    void limitFoodNutritionResults() {

        FoodNtrCpntResponse response =
                makeResponse(
                        List.of(
                                makeItem(
                                        "돼지국밥A",
                                        "음식",
                                        "외식_부산"
                                ),
                                makeItem(
                                        "돼지국밥B",
                                        "음식",
                                        "외식_부산"
                                ),
                                makeItem(
                                        "돼지국밥C",
                                        "음식",
                                        "외식_부산"
                                ),
                                makeItem(
                                        "돼지국밥D",
                                        "음식",
                                        "외식_부산"
                                )
                        )
                );

        List<FoodNtrCpntResponse.Item> result =
                foodNtrCpntHelper.filterFoodNutrition(
                        response,
                        "돼지국밥"
                );

        assertEquals(
                3,
                result.size()
        );
    }

    @Test
    @DisplayName("Null 영양정보 응답 빈 리스트 반환")
    void returnEmptyListWhenResponseNull() {

        List<FoodNtrCpntResponse.Item> result =
                foodNtrCpntHelper.filterFoodNutrition(
                        null,
                        "돼지국밥"
                );

        assertTrue(
                result.isEmpty()
        );
    }

    private FoodNtrCpntResponse makeResponse(
            List<FoodNtrCpntResponse.Item> items
    ) {

        return new FoodNtrCpntResponse(
                new FoodNtrCpntResponse.Header(
                        "00",
                        "NORMAL SERVICE."
                ),
                new FoodNtrCpntResponse.Body(
                        1,
                        items.size(),
                        10,
                        items
                )
        );
    }

    private FoodNtrCpntResponse.Item makeItem(
            String foodName,
            String dbGroupName,
            String foodOriginName
    ) {

        return new FoodNtrCpntResponse.Item(
                "FOOD_CODE",
                foodName,
                dbGroupName,
                foodOriginName,
                null,
                null,
                null,
                null,
                null,
                "10",
                "50",
                null,
                null,
                "500",
                null,
                null,
                null
        );
    }
}