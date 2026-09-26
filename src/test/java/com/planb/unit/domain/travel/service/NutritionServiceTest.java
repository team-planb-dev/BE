package com.planb.unit.domain.travel.service;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
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

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NutritionServiceTest {

    @Mock
    private FoodNtrCpntHandler foodNtrCpntHandler;

    @InjectMocks
    private NutritionService nutritionService;

    @Test
    @DisplayName("음식 이름 정확 일치 영양정보 선택")
    void evaluateFoodNutritionExactMatch() {

        // given
        String foodName = "비빔밥";
        List<DiseaseType> diseaseTypes =
                List.of(DiseaseType.DIABETES);

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
                        "0.5",
                        "70.0"
                );

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        ))
                .thenReturn(
                Mono.just(
                        List.of(
                                firstItem,
                                exactItem
                        )
                )
        );

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                diseaseTypes
                        )
                )
                .expectNextMatches(result ->
                        result.status() == NutritionEvaluationStatus.NOT_EVALUABLE
                                && result.evaluations().isEmpty()
                                && result.carbohydrate() == 50.0
                                && result.sodium() == 600.0
                                && result.fat() == 3.0
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("음식 이름 불일치 시 첫 번째 영양정보 선택")
    void evaluateFoodNutritionFirstItemFallback() {

        // given
        String foodName = "비빔밥";
        List<DiseaseType> diseaseTypes =
                List.of(DiseaseType.DIABETES);

        FoodNtrCpntResponse.Item firstItem =
                createItem(
                        "전주비빔밥",
                        "55.0",
                        "9.0",
                        "5.0",
                        "650.0",
                        "4.0",
                        "0.5",
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

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        ))
                .thenReturn(
                Mono.just(
                        List.of(
                                firstItem,
                                secondItem
                        )
                )
        );

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                diseaseTypes
                        )
                )
                .expectNextMatches(result ->
                        result.status() == NutritionEvaluationStatus.NOT_EVALUABLE
                                && result.evaluations().isEmpty()
                                && result.carbohydrate() == 55.0
                                && result.sodium() == 650.0
                                && result.fat() == 3.0
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("영양정보 조회 결과 없음")
    void evaluateFoodNutritionUnavailable() {

        // given
        String foodName = "없는음식";
        List<DiseaseType> diseaseTypes =
                List.of(DiseaseType.DIABETES);

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        ))
                .thenReturn(
                Mono.just(List.of())
        );

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                diseaseTypes
                        )
                )
                .expectNextMatches(result ->
                        result.diseaseTypes() == diseaseTypes
                                && result.status()
                                == NutritionEvaluationStatus.UNAVAILABLE
                                && result.evaluations().isEmpty()
                                && result.carbohydrate() == null
                                && result.sodium() == null
                                && result.fat() == null
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("빈 영양성분 원본 수치 보존")
    void evaluateFoodNutritionBlankNutritionValue() {

        // given
        String foodName = "비빔밥";
        List<DiseaseType> diseaseTypes =
                List.of(DiseaseType.DIABETES);

        FoodNtrCpntResponse.Item item =
                createItem(
                        foodName,
                        "",
                        "8.0",
                        "6.0",
                        "600.0",
                        "3.0",
                        "0.5",
                        "70.0"
                );

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        ))
                .thenReturn(
                Mono.just(List.of(item))
        );

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                diseaseTypes
                        )
                )
                .expectNextMatches(result ->
                        result.status() == NutritionEvaluationStatus.NOT_EVALUABLE
                                && result.evaluations().isEmpty()
                                && result.carbohydrate() == null
                                && result.sodium() == 600.0
                                && result.fat() == 3.0
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("1회분량 근거 없는 영양정보 평가 불가")
    void evaluateFoodNutritionWithoutReliableServing() {

        // given
        String foodName = "비빔밥";
        List<DiseaseType> diseaseTypes =
                List.of(DiseaseType.DIABETES);

        FoodNtrCpntResponse.Item item =
                createItem(
                        foodName,
                        "20.0",
                        "4.0",
                        "2.0",
                        "200.0",
                        "1.0",
                        "0.5",
                        "30.0"
                );

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        ))
                .thenReturn(
                Mono.just(List.of(item))
        );

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                diseaseTypes
                        )
                )
                .expectNextMatches(result ->
                        result.status()
                                == NutritionEvaluationStatus.NOT_EVALUABLE
                                && result.evaluations().isEmpty()
                                && result.carbohydrate() == 20.0
                                && result.sodium() == 200.0
                                && result.fat() == 3.0
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("영양정보 조회가 실패해도 예외 없이 조회 불가로 반환")
    void evaluateFoodNutritionWhenLookupFails() {

        // given
        String foodName = "비빔밥";
        List<DiseaseType> diseaseTypes =
                List.of(DiseaseType.DIABETES);

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        ))
                .thenReturn(
                Mono.error(
                        new RuntimeException("504 Gateway Timeout")
                )
        );

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                diseaseTypes
                        )
                )
                .expectNextMatches(result ->
                        result.status()
                                == NutritionEvaluationStatus.UNAVAILABLE
                                && result.evaluations().isEmpty()
                                && result.carbohydrate() == null
                                && result.sodium() == null
                                && result.fat() == null
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("메뉴명으로 못 찾으면 표준 품목명으로 다시 조회")
    void evaluateFoodNutritionRetriesWithStandardName() {

        // given
        String foodName = "검은콩 장칼국수";
        String standardFoodName = "칼국수";
        List<DiseaseType> diseaseTypes =
                List.of(DiseaseType.DIABETES);

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        ))
                .thenReturn(
                Mono.just(List.of()),
                Mono.just(List.of(createItem(
                        standardFoodName,
                        "10.0",
                        "1.0",
                        "1.0",
                        "100.0",
                        "1.0",
                        "0.0",
                        "0.0"
                )))
        );

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                standardFoodName,
                                diseaseTypes
                        )
                )
                .expectNextMatches(result ->
                        result.status() == NutritionEvaluationStatus.NOT_EVALUABLE
                                && result.evaluations().isEmpty()
                                && result.carbohydrate() == 10.0
                                && result.sodium() == 100.0
                )
                .verifyComplete();
    }

    @Test
    @DisplayName("표준 품목명이 없으면 다시 조회하지 않음")
    void evaluateFoodNutritionDoesNotRetryWithoutStandardName() {

        // given
        String foodName = "초당두부밥상";
        List<DiseaseType> diseaseTypes =
                List.of(DiseaseType.DIABETES);

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        ))
                .thenReturn(
                Mono.just(List.of())
        );

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                null,
                                diseaseTypes
                        )
                )
                .expectNextMatches(result ->
                        result.status() == NutritionEvaluationStatus.UNAVAILABLE
                )
                .verifyComplete();

        verify(foodNtrCpntHandler, times(1))
                .getFoodNutrition(any(FoodNtrCpntSearchRequest.class));
    }

    @Test
    @DisplayName("표준 품목명이 메뉴명과 같으면 다시 조회하지 않음")
    void evaluateFoodNutritionDoesNotRetryWithSameName() {

        // given
        String foodName = "칼국수";
        List<DiseaseType> diseaseTypes =
                List.of(DiseaseType.DIABETES);

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        ))
                .thenReturn(
                Mono.just(List.of())
        );

        // when & then
        StepVerifier.create(
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                foodName,
                                diseaseTypes
                        )
                )
                .expectNextMatches(result ->
                        result.status() == NutritionEvaluationStatus.UNAVAILABLE
                )
                .verifyComplete();

        verify(foodNtrCpntHandler, times(1))
                .getFoodNutrition(any(FoodNtrCpntSearchRequest.class));
    }

    @Test
    @DisplayName("첫 조회가 오래 걸려도 재조회는 자기 시간을 새로 받는다")
    void retryGetsItsOwnTimeoutBudget() {

        // given
        String foodName = "검은콩 장칼국수";
        String standardFoodName = "칼국수";
        List<DiseaseType> diseaseTypes =
                List.of(DiseaseType.DIABETES);

        FoodNtrCpntResponse.Item found = createItem(
                standardFoodName,
                "10.0",
                "1.0",
                "1.0",
                "100.0",
                "1.0",
                "0.0",
                "0.0"
        );

        // 두 조회가 예산을 나눠 쓰면 합이 상한을 넘어 타임아웃이 난다.
        // Mono는 구독 시점에 만들어야 가상시간 스케줄러를 잡는다.
        AtomicInteger calls = new AtomicInteger();

        when(foodNtrCpntHandler.getFoodNutrition(
                any(FoodNtrCpntSearchRequest.class)
        ))
                .thenAnswer(invocation -> Mono
                .just(calls.getAndIncrement() == 0
                        ? List.<FoodNtrCpntResponse.Item>of()
                        : List.of(found))
                .delayElement(Duration.ofSeconds(10)));

        // when & then
        StepVerifier.withVirtualTime(() ->
                        nutritionService.evaluateFoodNutrition(
                                foodName,
                                standardFoodName,
                                diseaseTypes
                        )
                )

                       .thenAwait(Duration.ofSeconds(30))
                .expectNextMatches(result ->
                        result.status() == NutritionEvaluationStatus.NOT_EVALUABLE
                )
                .verifyComplete();
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
