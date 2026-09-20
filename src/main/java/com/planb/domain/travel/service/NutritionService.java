package com.planb.domain.travel.service;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
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

    // 식약처 API가 죽으면 상대 게이트웨이가 60초를 끌고 504를 준다.
    // 그동안 AI 호출 전체가 매달리고, 메뉴 수만큼 곱해져 사용자가 몇 분을 기다린다.
    // 영양정보는 없어도 일정을 만들 수 있다. 빨리 포기하고 조회 불가로 넘긴다.
    // 정상 응답도 4~6초 걸린다. 그보다 넉넉히 잡아 살아 있는 응답을 자르지 않는다.
    private static final Duration LOOKUP_TIMEOUT =
            Duration.ofSeconds(15);

    private final FoodNtrCpntHandler foodNtrCpntHandler;

    // 음식 영양정보 조회 및 평가 가능 여부 판정
    public Mono<NutritionEvaluationResult> evaluateFoodNutrition(
            String foodName,
            List<DiseaseType> diseaseTypes
    ) {

        return evaluateFoodNutrition(foodName, null, diseaseTypes);
    }

    /**
     * 음식 영양정보 조회 및 평가 가능 여부 판정.
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

        // 현재 응답에는 신뢰 가능한 1회분량이 없다. 기준량 수치는 보존하되
        // 한 끼 임계값으로 평가하거나 건강 태그를 만들지 않는다.
        return new NutritionEvaluationResult(
                List.copyOf(diseaseTypes),
                NutritionEvaluationStatus.NOT_EVALUABLE,
                List.of(),
                parseNutritionValue(item.carbohydrate()),
                parseNutritionValue(item.sodium()),
                parseNutritionValue(item.fat())
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
