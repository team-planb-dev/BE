package com.planb.domain.travel.dto.nutrition;

public record NutritionInfo(
        Double carbohydrate,
        Double sugar,
        Double dietaryFiber,
        Double sodium,
        Double saturatedFat,
        Double transFat,
        Double cholesterol
) {
}