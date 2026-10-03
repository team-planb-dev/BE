package com.planb.domain.travel.service;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.global.client.foodNtrCpnt.dto.request.FoodNtrCpntSearchRequest;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.client.foodNtrCpnt.handler.FoodNtrCpntHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class NutritionService {

    // 식약처 API 장애 시 게이트웨이의 60초 지연과 504 응답
    // 메뉴 수만큼 누적되는 AI 호출·사용자 대기시간
    // 일정 생성에 필수가 아닌 영양정보의 조기 조회 포기
    // 정상 응답 4~6초를 고려한 조회 시간 상한
    private static final Duration LOOKUP_TIMEOUT =
            Duration.ofSeconds(15);

    private final FoodNtrCpntHandler foodNtrCpntHandler;

    private final MeterRegistry meterRegistry;

    // 음식 영양정보 조회 및 평가 가능 여부 판정
    public Mono<NutritionEvaluationResult> evaluateFoodNutrition(
            String foodName,
            List<DiseaseType> diseaseTypes
    ) {

        return evaluateFoodNutrition(
                foodName,
                null,
                diseaseTypes
        );
    }

    /**
     * 메뉴명 조회와 표준 품목명 재조회에 따른 영양 평가, 표준 품목명은 선택값
     */
    public Mono<NutritionEvaluationResult> evaluateFoodNutrition(
            String foodName,
            String standardFoodName,
            List<DiseaseType> diseaseTypes
    ) {

        return Mono.defer(() -> {
            AtomicBoolean retried = new AtomicBoolean();

            return lookup(foodName)
                    .flatMap(items -> {
                        if (items.isEmpty() && retryable(foodName, standardFoodName)) {
                            retried.set(true);

                            return lookup(standardFoodName)
                                    .map(result -> evaluated(
                                            result,
                                            standardFoodName,
                                            diseaseTypes
                                    ));
                        }

                        return Mono.just(
                                evaluated(
                                        items,
                                        foodName,
                                        diseaseTypes
                                )
                        );
                    })
                    .onErrorResume(failure -> {

                        log.warn(
                                "영양정보 조회 실패 - foodName: {}, 원인: {}",
                                foodName,
                                failure.toString()
                        );

                        return Mono.just(
                                unavailable(diseaseTypes)
                        );
                    })
                    .doOnNext(result -> recordEvaluation(
                            result.status(),
                            retried.get()
                    ));
        });
    }

    private void recordEvaluation(
            NutritionEvaluationStatus status,
            boolean retried
    ) {

        Counter
                .builder("planb.travel.nutrition.evaluation")
                .tag("status", status
                        .name()
                        .toLowerCase())
                .tag("retried", Boolean.toString(retried))
                .register(meterRegistry)
                .increment();
    }

    // 조회 건별 시간 상한과 재조회 독립 예산
    // 느린 첫 조회에 따른 재조회 예산 소진 방지
    private Mono<List<FoodNtrCpntResponse.Item>> lookup(String name) {

        return foodNtrCpntHandler
                .getFoodNutrition(
                        FoodNtrCpntSearchRequest.of(name)
                )
                .timeout(LOOKUP_TIMEOUT);
    }

    // 표준 품목명 누락·중복 시 재조회 제외
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
        // 조회 불가 영양정보의 식사 임계값 평가·건강 태그 생성 제외
        return new NutritionEvaluationResult(
                List.copyOf(diseaseTypes),
                NutritionEvaluationStatus.NOT_EVALUABLE,
                List.of(),
                parseNutritionValue(item.carbohydrate()),
                parseNutritionValue(item.sodium()),
                parseNutritionValue(item.fat())
        );
    }

    // 조회 실패 음식의 빈 영양 수치·평가
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

        return items
                .stream()
                .filter(item ->
                        item.foodName() != null
                                && item
                                        .foodName()
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
