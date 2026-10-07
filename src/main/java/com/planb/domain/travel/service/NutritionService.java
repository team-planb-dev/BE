package com.planb.domain.travel.service;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.repository.FoodNutritionCacheRepository;
import com.planb.global.client.foodNtrCpnt.dto.request.FoodNtrCpntSearchRequest;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.client.foodNtrCpnt.handler.FoodNtrCpntHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

    // 영양 조회 결과 보존 기간, 빈 결과는 메뉴명 표기 차이 가능성으로 짧게 유지
    private static final Duration FOUND_TTL = Duration.ofDays(30);

    private static final Duration EMPTY_TTL = Duration.ofDays(1);

    private final FoodNtrCpntHandler foodNtrCpntHandler;

    private final FoodNutritionCacheRepository foodNutritionCacheRepository;

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

    // 캐시 우선 조회, 미적중 시 식약처 API 조회 후 저장
    // 조회 건별 시간 상한과 재조회 독립 예산, 느린 첫 조회에 따른 재조회 예산 소진 방지
    private Mono<List<FoodNtrCpntResponse.Item>> lookup(String name) {

        String key = name.strip();

        return cached(key).switchIfEmpty(Mono.defer(() -> foodNtrCpntHandler
                .getFoodNutrition(
                        FoodNtrCpntSearchRequest.of(name)
                )
                .timeout(LOOKUP_TIMEOUT)
                .flatMap(items -> store(
                        key,
                        items
                ).thenReturn(items))));
    }

    // Redis 조회는 blocking이라 boundedElastic에서 실행, 실패 시 미적중으로 처리
    private Mono<List<FoodNtrCpntResponse.Item>> cached(String key) {

        return Mono
                .fromCallable(() -> foodNutritionCacheRepository.find(key))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(found -> {
                    recordCache(found.isPresent() ? "hit" : "miss");

                    return Mono.justOrEmpty(found);
                })
                .onErrorResume(failure -> {
                    log.warn(
                            "영양정보 캐시 조회 실패 - name: {}, 원인: {}",
                            key,
                            failure.toString()
                    );
                    recordCache("error");

                    return Mono.empty();
                });
    }

    // API 응답 스레드를 막지 않도록 boundedElastic에서 저장, 실패해도 조회 결과는 그대로 사용
    private Mono<Void> store(
            String key,
            List<FoodNtrCpntResponse.Item> items
    ) {

        return Mono
                .<Void>fromRunnable(() -> foodNutritionCacheRepository.save(
                        key,
                        items,
                        items.isEmpty()
                                ? EMPTY_TTL
                                : FOUND_TTL
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(failure -> {
                    log.warn(
                            "영양정보 캐시 저장 실패 - name: {}, 원인: {}",
                            key,
                            failure.toString()
                    );
                    recordCache("error");

                    return Mono.empty();
                });
    }

    private void recordCache(String result) {

        Counter
                .builder("planb.travel.nutrition.cache")
                .tag("result", result)
                .register(meterRegistry)
                .increment();
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
