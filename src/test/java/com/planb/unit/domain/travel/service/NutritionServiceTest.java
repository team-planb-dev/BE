package com.planb.unit.domain.travel.service;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.dto.nutrition.NutritionInfo;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.helper.NutritionEvaluator;
import com.planb.domain.travel.service.NutritionService;
import com.planb.global.client.foodNtrCpnt.dto.request.FoodNtrCpntSearchRequest;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.client.foodNtrCpnt.handler.FoodNtrCpntHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionServiceTest {

    @Mock
    private FoodNtrCpntHandler foodNtrCpntHandler;

    @Mock
    private NutritionEvaluator nutritionEvaluator;

    @InjectMocks
    private NutritionService nutritionService;

    @Test
    @DisplayName("음식 이름 정확 일치 영양정보 평가")
    void evaluateFoodNutritionExactMatch() {

        // given
        String foodName = "비빔밥";
        DiseaseType diseaseType = DiseaseType.DIABETES;

        FoodNtrCpntResponse.Item firstItem =
                createItem(
                        "전주비빔밥",
                        "60.0",
                        "10.0",
                        "5.0",
                        "700.0",
                        "4.0",
                        "0.2",
                        "80.0"
                );

        FoodNtrCpntResponse.Item exactItem =
                createItem(
                        "비빔밥",
                        "50.0",
                        "8.0",
                        "6.0",
                        "600.0",
                        "3.0",
                        "0.1",
                        "70.0"
                );

        NutritionInfo nutritionInfo =
                new NutritionInfo(
                        50.0,
                        8.0,
                        6.0,
                        600.0,
                        3.0,
                        0.1,
                        70.0,
                        3.0
                );

        NutritionEvaluationResult expectedResult =
                new NutritionEvaluationResult(
                        diseaseType,
                        NutritionEvaluationStatus.AVAILABLE,
                        List.of(),
                        50.0,
                        600.0,
                        3.0
                );

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        )).thenReturn(
                Mono.just(
                        List.of(
                                firstItem,
                                exactItem
                        )
                )
        );

        when(nutritionEvaluator.evaluate(
                diseaseType,
                nutritionInfo
        )).thenReturn(expectedResult);

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                diseaseType
                        )
                )
                .expectNext(expectedResult)
                .verifyComplete();

        verify(nutritionEvaluator)
                .evaluate(
                        diseaseType,
                        nutritionInfo
                );
    }

    @Test
    @DisplayName("음식 이름 불일치 시 첫 번째 영양정보 평가")
    void evaluateFoodNutritionFirstItemFallback() {

        // given
        String foodName = "비빔밥";
        DiseaseType diseaseType = DiseaseType.DIABETES;

        FoodNtrCpntResponse.Item firstItem =
                createItem(
                        "전주비빔밥",
                        "55.0",
                        "9.0",
                        "5.0",
                        "650.0",
                        "4.0",
                        "0.2",
                        "75.0"
                );

        FoodNtrCpntResponse.Item secondItem =
                createItem(
                        "산채비빔밥",
                        "45.0",
                        "7.0",
                        "7.0",
                        "500.0",
                        "2.0",
                        "0.1",
                        "60.0"
                );

        NutritionInfo nutritionInfo =
                new NutritionInfo(
                        55.0,
                        9.0,
                        5.0,
                        650.0,
                        4.0,
                        0.2,
                        75.0,
                        3.0
                );

        NutritionEvaluationResult expectedResult =
                new NutritionEvaluationResult(
                        diseaseType,
                        NutritionEvaluationStatus.AVAILABLE,
                        List.of(),
                        55.0,
                        650.0,
                        3.0
                );

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        )).thenReturn(
                Mono.just(
                        List.of(
                                firstItem,
                                secondItem
                        )
                )
        );

        when(nutritionEvaluator.evaluate(
                diseaseType,
                nutritionInfo
        )).thenReturn(expectedResult);

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                diseaseType
                        )
                )
                .expectNext(expectedResult)
                .verifyComplete();

        verify(nutritionEvaluator)
                .evaluate(
                        diseaseType,
                        nutritionInfo
                );
    }

    @Test
    @DisplayName("영양정보 조회 결과 없음")
    void evaluateFoodNutritionUnavailable() {

        // given
        String foodName = "없는음식";
        DiseaseType diseaseType = DiseaseType.DIABETES;

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        )).thenReturn(
                Mono.just(List.of())
        );

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                diseaseType
                        )
                )
                .expectNextMatches(result ->
                        result.diseaseType() == diseaseType
                                && result.status()
                                == NutritionEvaluationStatus.UNAVAILABLE
                                && result.evaluations().isEmpty()
                                && result.carbohydrate() == null
                                && result.sodium() == null
                                && result.fat() == null
                )
                .verifyComplete();

        verify(nutritionEvaluator, never())
                .evaluate(
                        any(DiseaseType.class),
                        any(NutritionInfo.class)
                );
    }

    @Test
    @DisplayName("빈 영양성분 평가용 데이터 변환")
    void evaluateFoodNutritionBlankNutritionValue() {

        // given
        String foodName = "비빔밥";
        DiseaseType diseaseType = DiseaseType.DIABETES;

        FoodNtrCpntResponse.Item item =
                createItem(
                        foodName,
                        "",
                        "8.0",
                        "6.0",
                        "600.0",
                        "3.0",
                        "0.1",
                        "70.0"
                );

        NutritionInfo nutritionInfo =
                new NutritionInfo(
                        null,
                        8.0,
                        6.0,
                        600.0,
                        3.0,
                        0.1,
                        70.0,
                        3.0
                );

        NutritionEvaluationResult expectedResult =
                new NutritionEvaluationResult(
                        diseaseType,
                        NutritionEvaluationStatus.NOT_EVALUABLE,
                        List.of(),
                        null,
                        600.0,
                        3.0
                );

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        )).thenReturn(
                Mono.just(List.of(item))
        );

        when(nutritionEvaluator.evaluate(
                diseaseType,
                nutritionInfo
        )).thenReturn(expectedResult);

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                diseaseType
                        )
                )
                .expectNext(expectedResult)
                .verifyComplete();

        verify(nutritionEvaluator)
                .evaluate(
                        diseaseType,
                        nutritionInfo
                );
    }

    private FoodNtrCpntResponse.Item createItem(
            String foodName,
            String carbohydrate,
            String sugar,
            String dietaryFiber,
            String sodium,
            String saturatedFat,
            String transFat,
            String cholesterol
    ) {

        return new FoodNtrCpntResponse.Item(
                "FOOD001",
                foodName,
                "음식DB",
                "",
                "",
                "",
                "100g",
                "100.0",
                "5.0",
                "3.0",
                carbohydrate,
                sugar,
                dietaryFiber,
                sodium,
                cholesterol,
                saturatedFat,
                transFat
        );
    }
}