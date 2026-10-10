package com.planb.ai.handler;

import com.planb.ai.context.PlanCandidateSnapshot;
import com.planb.ai.context.TravelPlanContext;
import com.planb.domain.travel.policy.MealSlotPolicy;
import com.planb.ai.mcp.TourismTool;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/**
 * AI 일정 생성 전 지역 후보 조회
 */
@Component
@RequiredArgsConstructor
public class PlanCandidatePrefetcher {

    private static final int MAX_KEYWORDS = 8;

    private static final int CANDIDATES_PER_KEYWORD = 8;

    private static final int MAX_RESTAURANT_CANDIDATES = 40;

    private static final Duration PREFETCH_TIMEOUT = Duration.ofSeconds(60);

    private final TourismTool tourismTool;

    public Mono<PlanCandidateSnapshot> prepare(TravelPlanContext context) {

        return Mono.defer(() -> prepareCandidates(context));
    }

    private Mono<PlanCandidateSnapshot> prepareCandidates(TravelPlanContext context) {

        var request = context.createTravelRequest();

        Mono<AttractionCandidates> attractions = Mono
                .fromCallable(() -> tourismTool.findPlannedPlaces(
                        request.plannedPlaces(),
                        request.locationDo(),
                        request.locationSigungu()
                ))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(planned -> tourismTool
                        .findAttractionCandidates(
                                request.locationDo(),
                                request.locationSigungu()
                        )
                        .map(response -> new AttractionCandidates(
                                items(response, "12"),
                                planned
                                        .stream()
                                        .filter(item -> validCandidate(item, "12"))
                                        .toList()
                        )));

        Mono<RestaurantCandidates> restaurants = Flux
                .fromIterable(keywords(context))
                .concatMap(keyword -> tourismTool
                        .findRestaurantCandidates(
                                keyword,
                                request.locationDo(),
                                request.locationSigungu()
                        )
                        .switchIfEmpty(Mono.error(new IllegalStateException("선조회 음식점 응답 누락"))))
                .map(response -> uniqueCandidates(
                        items(response, "39"),
                        CANDIDATES_PER_KEYWORD
                ))
                .collectList()
                .map(batches -> uniqueCandidates(
                        batches
                                .stream()
                                .flatMap(List::stream)
                                .toList(),
                        MAX_RESTAURANT_CANDIDATES
                ))
                .flatMap(found -> {
                    if (found.size() >= requiredMealCount(context)) {
                        return Mono.just(new RestaurantCandidates(found, false));
                    }

                    return tourismTool
                            .findRegionalRestaurantCandidates(
                                    request.locationDo(),
                                    request.locationSigungu()
                            )
                            .map(response -> {
                                List<Kor2KeywordSearchResponse.Item> combined = new ArrayList<>(found);
                                combined.addAll(items(response, "39"));

                                return new RestaurantCandidates(
                                        uniqueCandidates(combined, MAX_RESTAURANT_CANDIDATES),
                                        true
                                );
                            });
                });

        return Mono
                .zip(attractions, restaurants)
                .map(results -> new PlanCandidateSnapshot(
                        results.getT1().items(),
                        results.getT2().items(),
                        results.getT1().plannedPlaces(),
                        results.getT2().regional()
                ))
                .switchIfEmpty(Mono.error(new IllegalStateException("선조회 외부 응답 누락")))
                .timeout(PREFETCH_TIMEOUT);
    }

    private record AttractionCandidates(
            List<Kor2KeywordSearchResponse.Item> items,
            List<Kor2KeywordSearchResponse.Item> plannedPlaces
    ) {
    }

    private int requiredMealCount(TravelPlanContext context) {

        int days = context
                .createTravelRequest()
                .dateType()
                .getPlusDays() + 1;
        int count = 0;

        for (var mealType : MealSlotPolicy.MEAL_SCHEDULE_TYPES) {
            if (MealSlotPolicy.configuredMealTime(mealType, context.healthContexts()) != null) {
                count += switch (mealType) {
                    case BREAKFAST, DINNER -> days - 1;
                    default -> days;
                };
            }
        }

        return count;
    }

    private record RestaurantCandidates(
            List<Kor2KeywordSearchResponse.Item> items,
            boolean regional
    ) {
    }

    private List<String> keywords(TravelPlanContext context) {

        var request = context.createTravelRequest();
        List<String> keywords = new ArrayList<>();

        if (request.localFoods() != null) {
            keywords.addAll(request.localFoods());
        }
        if (request.recommendFoods() != null) {
            keywords.addAll(request.recommendFoods());
        }

        return keywords
                .stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(keyword -> !keyword.isBlank())
                .distinct()
                .limit(MAX_KEYWORDS)
                .toList();
    }

    private List<Kor2KeywordSearchResponse.Item> items(
            Kor2KeywordSearchResponse response,
            String contentTypeId
    ) {

        if (response == null || response.response() == null) {
            throw new IllegalStateException("선조회 외부 응답 누락");
        }

        String resultCode = response.resultCode();

        if (resultCode != null && !"0000".equals(resultCode) && !"00".equals(resultCode)) {
            throw new IllegalStateException("선조회 외부 API 오류: " + resultCode);
        }

        var body = response
                .response()
                .body();

        if (body == null || body.items() == null || body.items().item() == null) {
            throw new IllegalStateException("선조회 외부 응답 본문 누락");
        }

        return response
                .response()
                .body()
                .items()
                .item()
                .stream()
                .filter(item -> validCandidate(item, contentTypeId))
                .toList();
    }

    private List<Kor2KeywordSearchResponse.Item> uniqueCandidates(
            List<Kor2KeywordSearchResponse.Item> items,
            int limit
    ) {

        var unique = new LinkedHashMap<String, Kor2KeywordSearchResponse.Item>();

        items.forEach(item -> unique.putIfAbsent(item.contentid(), item));

        return unique
                .values()
                .stream()
                .limit(limit)
                .toList();
    }

    private boolean validCandidate(
            Kor2KeywordSearchResponse.Item item,
            String contentTypeId
    ) {

        return item != null
                && contentTypeId.equals(item.contenttypeid())
                && item.contentid() != null
                && !item.contentid().isBlank();
    }
}
