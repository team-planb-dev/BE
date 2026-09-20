package com.planb.domain.travel.service;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.dto.nutrition.NutritionInfo;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.helper.NutritionEvaluator;
import com.planb.global.client.foodNtrCpnt.dto.request.FoodNtrCpntSearchRequest;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.client.foodNtrCpnt.handler.FoodNtrCpntHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionService {

    // NutritionThreshold는 한 끼 기준이라 기준량 수치를 그대로 재면 어떤 메뉴든 낮게 나온다.
    // 한 끼를 이 중량으로 보고 맞춘 값으로 평가한다.
    //
    // 이 상수는 임시 가정이다. SERVING_SIZE는 영양성분 기준량이고 단위도 g과 ml가 섞인다.
    // Z10500은 식품중량일 뿐 1인분으로 정의되지 않아 전역 환산 근거로 쓸 수 없다.
    // 근거와 실측은 docs/ai/mfds-nutrition-api-verification.md 참고.
    private static final double REFERENCE_SERVING_GRAMS = 300.0;

    private static final double SERVING_RATIO =
            REFERENCE_SERVING_GRAMS / 100.0;

    // 식약처 API가 죽으면 상대 게이트웨이가 60초를 끌고 504를 준다.
    // 그동안 AI 호출 전체가 매달리고, 메뉴 수만큼 곱해져 사용자가 몇 분을 기다린다.
    // 영양정보는 없어도 일정을 만들 수 있다. 빨리 포기하고 조회 불가로 넘긴다.
    // 정상 응답도 4~6초 걸린다. 그보다 넉넉히 잡아 살아 있는 응답을 자르지 않는다.
    private static final Duration LOOKUP_TIMEOUT =
            Duration.ofSeconds(15);

    private final FoodNtrCpntHandler foodNtrCpntHandler;
    private final NutritionEvaluator nutritionEvaluator;

    // 음식 영양정보 조회 및 질환별 영양성분 평가
    public Mono<NutritionEvaluationResult> evaluateFoodNutrition(
            String foodName,
            List<DiseaseType> diseaseTypes
    ) {

        return evaluateFoodNutrition(foodName, null, diseaseTypes);
    }

    /**
     * 음식 영양정보 조회 및 질환별 영양성분 평가.
     *
     * 식당 고유 메뉴명은 식약처 품목명이 아니라서 조회되지 않는 경우가 많다.
     * 그래서 메뉴명으로 못 찾으면 표준 품목명으로 한 번 더 조회한다.
     *
     * 표준 품목명은 여기서만 쓴다. 결과를 되찾는 쪽은 메뉴명을 키로 쓰므로
     * (PlanService가 restaurantDetail.menuName()으로 찾는다) 바깥으로 새면
     * 조회는 성공하는데 화면은 그대로 빈칸이 된다.
     *
     * @param foodName         식당이 내건 메뉴명
     * @param standardFoodName 같은 음식의 표준 품목명, 없으면 null
     */
    public Mono<NutritionEvaluationResult> evaluateFoodNutrition(
            String foodName,
            String standardFoodName,
            List<DiseaseType> diseaseTypes
    ) {

        return lookup(foodName)
                .flatMap(items -> items.isEmpty() && retryable(foodName, standardFoodName)
                        ? lookup(standardFoodName)
                        .map(retried -> evaluated(retried, standardFoodName, diseaseTypes))
                        : Mono.just(evaluated(items, foodName, diseaseTypes)))
                .onErrorResume(failure -> {

                    log.warn(
                            "영양정보 조회 실패 - foodName: {}, 원인: {}",
                            foodName,
                            failure.toString()
                    );

                    return Mono.just(
                            unavailable(diseaseTypes)
                    );
                });
    }

    // 조회 한 건에 상한을 건다. 재조회는 첫 조회와 예산을 나눠 쓰지 않는다.
    // 합쳐서 재면 첫 조회가 느린 날 재조회가 시작도 못 하고 잘린다.
    private Mono<List<FoodNtrCpntResponse.Item>> lookup(String name) {

        return foodNtrCpntHandler
                .getFoodNutrition(
                        FoodNtrCpntSearchRequest.of(name)
                )
                .timeout(LOOKUP_TIMEOUT);
    }

    // 표준 품목명이 비어 있거나 메뉴명과 같으면 같은 조회를 두 번 하는 셈이다
    private boolean retryable(
            String foodName,
            String standardFoodName
    ) {

        return standardFoodName != null
                && !standardFoodName.isBlank()
                && !standardFoodName.equals(foodName);
    }

    private NutritionEvaluationResult evaluated(
            List<FoodNtrCpntResponse.Item> items,
            String foodName,
            List<DiseaseType> diseaseTypes
    ) {

        if (items.isEmpty()) {
            return unavailable(diseaseTypes);
        }

        FoodNtrCpntResponse.Item item =
                findFoodItem(
                        items,
                        foodName
                );

        NutritionInfo measured =
                toNutritionInfo(item);

        NutritionEvaluationResult evaluated =
                nutritionEvaluator.evaluate(
                        diseaseTypes,
                        toReferenceServing(measured)
                );

        return withMeasuredValues(
                evaluated,
                measured
        );
    }

    // 조회하지 못한 음식의 결과, 수치와 평가가 모두 비어 있다
    private NutritionEvaluationResult unavailable(
            List<DiseaseType> diseaseTypes
    ) {

        return new NutritionEvaluationResult(
                List.copyOf(diseaseTypes),
                NutritionEvaluationStatus.UNAVAILABLE,
                List.of(),
                null,
                null,
                null
        );
    }

    // 음식 이름 정확 일치 우선 조회, 없으면 첫 번째 결과 반환
    private FoodNtrCpntResponse.Item findFoodItem(
            List<FoodNtrCpntResponse.Item> items,
            String foodName
    ) {

        return items.stream()
                .filter(item ->
                        item.foodName() != null
                                && item.foodName()
                                .trim()
                                .equalsIgnoreCase(
                                        foodName.trim()
                                )
                )
                .findFirst()
                .orElse(items.getFirst());
    }

    // 식약처 영양정보를 평가용 NutritionInfo로 변환
    private NutritionInfo toNutritionInfo(
            FoodNtrCpntResponse.Item item
    ) {

        return new NutritionInfo(
                parseNutritionValue(item.carbohydrate()),
                parseNutritionValue(item.sugar()),
                parseNutritionValue(item.dietaryFiber()),
                parseNutritionValue(item.sodium()),
                parseNutritionValue(item.saturatedFat()),
                parseNutritionValue(item.transFat()),
                parseNutritionValue(item.cholesterol()),
                parseNutritionValue(item.fat())
        );
    }

    // 100g 기준 수치를 기준 1인분 분량으로 환산 (평가 전용)
    private NutritionInfo toReferenceServing(
            NutritionInfo measured
    ) {

        return new NutritionInfo(
                toServing(measured.carbohydrate()),
                toServing(measured.sugar()),
                toServing(measured.dietaryFiber()),
                toServing(measured.sodium()),
                toServing(measured.saturatedFat()),
                toServing(measured.transFat()),
                toServing(measured.cholesterol()),
                toServing(measured.fat())
        );
    }

    private Double toServing(Double value) {

        return value == null
                ? null
                : value * SERVING_RATIO;
    }

    /**
     * 평가 결과의 수치를 실측값으로 되돌린다.
     *
     * 등급은 한 끼 분량을 가정해 매기지만 화면에 나가는 수치는 측정값이어야 한다.
     * 환산값을 내보내면 가정이 측정처럼 보인다.
     */
    private NutritionEvaluationResult withMeasuredValues(
            NutritionEvaluationResult evaluated,
            NutritionInfo measured
    ) {

        return new NutritionEvaluationResult(
                evaluated.diseaseTypes(),
                evaluated.status(),
                evaluated.evaluations(),
                measured.carbohydrate(),
                measured.sodium(),
                measured.fat()
        );
    }

    // 식약처 String 영양성분 값을 Double 타입으로 변환
    private Double parseNutritionValue(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        return Double.parseDouble(
                value.trim()
        );
    }
}
