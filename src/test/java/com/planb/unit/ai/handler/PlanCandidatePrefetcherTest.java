package com.planb.unit.ai.handler;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.handler.PlanCandidatePrefetcher;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.global.client.kor2Service.Kor2ServiceClient;
import com.planb.global.client.kor2Service.dto.response.Kor2AreaCodeResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.time.LocalDate;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanCandidatePrefetcherTest {

    private final Kor2ServiceClient client = mock(Kor2ServiceClient.class);

    private final JsonMapper mapper = JsonMapper
            .builder()
            .findAndAddModules()
            .build();

    @Test
    void preparesCanonicalAttractionsBeforeSelection() throws Exception {

        when(client.baseUrl()).thenReturn("http://stub.invalid");
        when(client.serviceKey()).thenReturn("stub-key");
        when(client.get(any(URI.class), eq(Kor2AreaCodeResponse.class)))
                .thenReturn(Mono.just(fixture("area-codes.json", Kor2AreaCodeResponse.class)));
        when(client.get(any(URI.class), eq(Kor2KeywordSearchResponse.class)))
                .thenReturn(Mono.just(fixture("attractions.json", Kor2KeywordSearchResponse.class)));

        TourismTool tourism = new TourismTool(
                new Kor2ServiceHandler(client),
                null,
                null,
                null
        );

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        new PlanCandidatePrefetcher(tourism)
                .prepare(context(List.of(), List.of()))
                .block()
                .restore(candidates);

        assertThat(candidates.attractionCandidates()).isNotEmpty();
        assertThat(candidates.find("tour:900001").longitude()).isEqualTo("126.9700");
        assertThat(candidates.find("tour:900001").name()).isEqualTo("스텁 관광지 01");
        assertThat(candidates.restaurantCandidates()).isEmpty();
    }

    @Test
    void preparesRequestedRestaurantKeywordsWithoutDuplicateCandidates() throws Exception {

        when(client.baseUrl()).thenReturn("http://stub.invalid");
        when(client.serviceKey()).thenReturn("stub-key");
        when(client.get(any(URI.class), eq(Kor2AreaCodeResponse.class)))
                .thenReturn(Mono.just(fixture("area-codes.json", Kor2AreaCodeResponse.class)));

        var attractions = fixture("attractions.json", Kor2KeywordSearchResponse.class);
        var restaurants = fixture("restaurants.json", Kor2KeywordSearchResponse.class);
        var keywords = new java.util.concurrent.CopyOnWriteArrayList<String>();

        when(client.get(any(URI.class), eq(Kor2KeywordSearchResponse.class)))
                .thenAnswer(call -> {
                    URI uri = call.getArgument(0);
                    if (uri.getPath().endsWith("searchKeyword2")) {
                        keywords.add(java.net.URLDecoder.decode(uri.getQuery(), java.nio.charset.StandardCharsets.UTF_8));
                        return Mono.just(restaurants);
                    }
                    return Mono.just(attractions);
                });

        TourismTool tourism = new TourismTool(
                new Kor2ServiceHandler(client),
                null,
                null,
                null
        );
        PlaceCandidateContext candidates = new PlaceCandidateContext();

        new PlanCandidatePrefetcher(tourism)
                .prepare(context(List.of(" 쌈밥 ", "쌈밥"), List.of("", "불고기", "쌈밥")))
                .block()
                .restore(candidates);

        assertThat(candidates.restaurantCandidates()).hasSize(8);
        assertThat(candidates.find("tour:910001").name()).isEqualTo("스텁 음식점 01");
        assertThat(keywords).hasSize(2);
        assertThat(keywords.getFirst()).contains("keyword=쌈밥");
        assertThat(keywords.getLast()).contains("keyword=불고기");
    }

    @Test
    void fillsMissingMealCandidatesFromTheSameRegion() throws Exception {

        when(client.baseUrl()).thenReturn("http://stub.invalid");
        when(client.serviceKey()).thenReturn("stub-key");
        when(client.get(any(URI.class), eq(Kor2AreaCodeResponse.class)))
                .thenReturn(Mono.just(fixture("area-codes.json", Kor2AreaCodeResponse.class)));
        var attractions = fixture("attractions.json", Kor2KeywordSearchResponse.class);
        var restaurants = fixture("restaurants.json", Kor2KeywordSearchResponse.class);

        when(client.get(any(URI.class), eq(Kor2KeywordSearchResponse.class)))
                .thenAnswer(call -> {
                    URI uri = call.getArgument(0);
                    return Mono.just(uri.getQuery().contains("contentTypeId=39") ? restaurants : attractions);
                });

        var health = new com.planb.ai.context.TravelHealthContext(
                "동행인",
                List.of(),
                com.planb.domain.health.entity.constant.WalkType.ACTIVE,
                new com.planb.ai.context.TravelHealthContext.MealInfoContext(
                        null,
                        java.time.LocalTime.NOON,
                        null
                ),
                List.of(),
                List.of()
        );
        var request = context(List.of(), List.of()).createTravelRequest();

        TourismTool tourism = new TourismTool(
                new Kor2ServiceHandler(client),
                null,
                null,
                null
        );
        var snapshot = new PlanCandidatePrefetcher(tourism)
                .prepare(new TravelPlanContext(request, List.of(health)))
                .block();

        assertThat(snapshot.restaurants()).hasSize(8);
        assertThat(snapshot.regionalRestaurantsCollected()).isTrue();
    }

    @Test
    void boundsKeywordsAndRestaurantCandidateInput() throws Exception {

        when(client.baseUrl()).thenReturn("http://stub.invalid");
        when(client.serviceKey()).thenReturn("stub-key");
        when(client.get(any(URI.class), eq(Kor2AreaCodeResponse.class)))
                .thenReturn(Mono.just(fixture("area-codes.json", Kor2AreaCodeResponse.class)));
        var attractions = fixture("attractions.json", Kor2KeywordSearchResponse.class);
        var restaurants = fixture("restaurants.json", Kor2KeywordSearchResponse.class);
        var queries = new java.util.concurrent.atomic.AtomicInteger();

        when(client.get(any(URI.class), eq(Kor2KeywordSearchResponse.class)))
                .thenAnswer(call -> {
                    URI uri = call.getArgument(0);
                    if (!uri.getPath().endsWith("searchKeyword2")) {
                        return Mono.just(attractions);
                    }
                    int query = queries.incrementAndGet();
                    String json = mapper.writeValueAsString(restaurants).replace("9100", "91" + query + "0");
                    return Mono.just(mapper.readValue(json, Kor2KeywordSearchResponse.class));
                });

        var foods = java.util.stream.IntStream
                .rangeClosed(1, 9)
                .mapToObj(number -> "메뉴" + number)
                .toList();
        TourismTool tourism = new TourismTool(
                new Kor2ServiceHandler(client),
                null,
                null,
                null
        );
        var snapshot = new PlanCandidatePrefetcher(tourism)
                .prepare(context(foods, List.of()))
                .block();

        assertThat(queries.get()).isEqualTo(8);
        assertThat(snapshot.restaurants()).hasSize(40);
    }

    @Test
    void rejectsFailedExternalResultWithoutUsingItsCandidates() throws Exception {

        when(client.baseUrl()).thenReturn("http://stub.invalid");
        when(client.serviceKey()).thenReturn("stub-key");
        when(client.get(any(URI.class), eq(Kor2AreaCodeResponse.class)))
                .thenReturn(Mono.just(fixture("area-codes.json", Kor2AreaCodeResponse.class)));

        var attractions = fixture("attractions.json", Kor2KeywordSearchResponse.class);
        var restaurants = fixture("restaurants.json", Kor2KeywordSearchResponse.class);
        var failed = new Kor2KeywordSearchResponse(
                new Kor2KeywordSearchResponse.Response(
                        new Kor2KeywordSearchResponse.Header("23", "rate limit"),
                        restaurants.response().body()
                )
        );

        when(client.get(any(URI.class), eq(Kor2KeywordSearchResponse.class)))
                .thenAnswer(call -> {
                    URI uri = call.getArgument(0);

                    return Mono.just(uri.getPath().endsWith("searchKeyword2") ? failed : attractions);
                });

        TourismTool tourism = new TourismTool(
                new Kor2ServiceHandler(client),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> new PlanCandidatePrefetcher(tourism)
                .prepare(context(List.of("불고기"), List.of()))
                .block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("23");
    }

    @Test
    void rejectsMissingKeywordResponseInsteadOfTreatingItAsEmptyCandidates() throws Exception {

        when(client.baseUrl()).thenReturn("http://stub.invalid");
        when(client.serviceKey()).thenReturn("stub-key");
        when(client.get(any(URI.class), eq(Kor2AreaCodeResponse.class)))
                .thenReturn(Mono.just(fixture("area-codes.json", Kor2AreaCodeResponse.class)));

        var attractions = fixture("attractions.json", Kor2KeywordSearchResponse.class);

        when(client.get(any(URI.class), eq(Kor2KeywordSearchResponse.class)))
                .thenAnswer(call -> {
                    URI uri = call.getArgument(0);

                    return uri.getPath().endsWith("searchKeyword2") ? Mono.empty() : Mono.just(attractions);
                });

        TourismTool tourism = new TourismTool(
                new Kor2ServiceHandler(client),
                null,
                null,
                null
        );

        assertThatThrownBy(() -> new PlanCandidatePrefetcher(tourism)
                .prepare(context(List.of("불고기"), List.of()))
                .block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("응답 누락");
    }

    @Test
    void doesNotPinPlannedPlacesWithoutCanonicalIdentity() throws Exception {

        when(client.baseUrl()).thenReturn("http://stub.invalid");
        when(client.serviceKey()).thenReturn("stub-key");
        when(client.get(any(URI.class), eq(Kor2AreaCodeResponse.class)))
                .thenReturn(Mono.just(fixture("area-codes.json", Kor2AreaCodeResponse.class)));

        var attractions = fixture("attractions.json", Kor2KeywordSearchResponse.class);
        String invalidJson = mapper
                .writeValueAsString(attractions)
                .replace("스텁시 스텁구", "서울 종로구")
                .replace("\"contentid\":\"900001\"", "\"contentid\":null");
        var invalid = mapper.readValue(invalidJson, Kor2KeywordSearchResponse.class);

        when(client.get(any(URI.class), eq(Kor2KeywordSearchResponse.class)))
                .thenAnswer(call -> {
                    URI uri = call.getArgument(0);

                    return Mono.just(uri.getPath().endsWith("searchKeyword2") ? invalid : attractions);
                });

        TourismTool tourism = new TourismTool(
                new Kor2ServiceHandler(client),
                null,
                null,
                null
        );
        var snapshot = new PlanCandidatePrefetcher(tourism)
                .prepare(context(
                        List.of(),
                        List.of(),
                        List.of(new CreateTravelRequest.PlannedPlaceDetail("스텁 관광지 01", "서울 종로구"))
                ))
                .block();

        assertThat(snapshot.plannedPlaces()).isEmpty();
    }

    @Test
    void timesOutAndCancelsPendingExternalLookups() throws Exception {

        when(client.baseUrl()).thenReturn("http://stub.invalid");
        when(client.serviceKey()).thenReturn("stub-key");
        when(client.get(any(URI.class), eq(Kor2AreaCodeResponse.class)))
                .thenReturn(Mono.just(fixture("area-codes.json", Kor2AreaCodeResponse.class)));

        AtomicBoolean cancelled = new AtomicBoolean();

        when(client.get(any(URI.class), eq(Kor2KeywordSearchResponse.class)))
                .thenReturn(Mono.<Kor2KeywordSearchResponse>never()
                        .doOnCancel(() -> cancelled.set(true)));

        TourismTool tourism = new TourismTool(
                new Kor2ServiceHandler(client),
                null,
                null,
                null
        );

        StepVerifier
                .withVirtualTime(() -> new PlanCandidatePrefetcher(tourism)
                        .prepare(context(List.of("불고기"), List.of())))
                .thenAwait(Duration.ofSeconds(60))
                .expectError(TimeoutException.class)
                .verify(Duration.ofSeconds(5));

        assertThat(cancelled).isTrue();
    }

    @Test
    void cancelsOtherLookupWhenOneBranchFails() throws Exception {

        when(client.baseUrl()).thenReturn("http://stub.invalid");
        when(client.serviceKey()).thenReturn("stub-key");
        when(client.get(any(URI.class), eq(Kor2AreaCodeResponse.class)))
                .thenReturn(Mono.just(fixture("area-codes.json", Kor2AreaCodeResponse.class)));

        AtomicBoolean restaurantCancelled = new AtomicBoolean();

        when(client.get(any(URI.class), eq(Kor2KeywordSearchResponse.class)))
                .thenAnswer(call -> {
                    URI uri = call.getArgument(0);

                    if (uri.getPath().endsWith("searchKeyword2")) {
                        return Mono.<Kor2KeywordSearchResponse>never()
                                .doOnCancel(() -> restaurantCancelled.set(true));
                    }

                    return Mono
                            .delay(Duration.ofSeconds(1))
                            .then(Mono.error(new IllegalStateException("외부 조회 실패")));
                });

        TourismTool tourism = new TourismTool(
                new Kor2ServiceHandler(client),
                null,
                null,
                null
        );

        StepVerifier
                .withVirtualTime(() -> new PlanCandidatePrefetcher(tourism)
                        .prepare(context(List.of("불고기"), List.of())))
                .thenAwait(Duration.ofSeconds(1))
                .expectErrorMessage("외부 조회 실패")
                .verify(Duration.ofSeconds(5));

        assertThat(restaurantCancelled).isTrue();
    }

    @Test
    void keepsCandidateSnapshotsSeparateAcrossConcurrentRequests() throws Exception {

        when(client.baseUrl()).thenReturn("http://stub.invalid");
        when(client.serviceKey()).thenReturn("stub-key");
        when(client.get(any(URI.class), eq(Kor2AreaCodeResponse.class)))
                .thenReturn(Mono.just(fixture("area-codes.json", Kor2AreaCodeResponse.class)));

        var attractions = fixture("attractions.json", Kor2KeywordSearchResponse.class);
        var restaurants = fixture("restaurants.json", Kor2KeywordSearchResponse.class);
        var otherRestaurants = mapper.readValue(
                mapper
                        .writeValueAsString(restaurants)
                        .replace("9100", "9200"),
                Kor2KeywordSearchResponse.class
        );

        when(client.get(any(URI.class), eq(Kor2KeywordSearchResponse.class)))
                .thenAnswer(call -> {
                    URI uri = call.getArgument(0);
                    String query = java.net.URLDecoder.decode(
                            uri.getQuery(),
                            java.nio.charset.StandardCharsets.UTF_8
                    );
                    var response = uri.getPath().endsWith("searchKeyword2")
                            ? query.contains("keyword=불고기") ? restaurants : otherRestaurants
                            : attractions;

                    return Mono.just(response).delayElement(Duration.ofMillis(25));
                });

        TourismTool tourism = new TourismTool(
                new Kor2ServiceHandler(client),
                null,
                null,
                null
        );
        PlanCandidatePrefetcher prefetcher = new PlanCandidatePrefetcher(tourism);

        var snapshots = Mono
                .zip(
                        prefetcher.prepare(context(List.of("불고기"), List.of())),
                        prefetcher.prepare(context(List.of("비빔밥"), List.of()))
                )
                .block();
        PlaceCandidateContext first = new PlaceCandidateContext();
        PlaceCandidateContext second = new PlaceCandidateContext();
        snapshots.getT1().restore(first);
        snapshots.getT2().restore(second);

        assertThat(first.find("tour:910001")).isNotNull();
        assertThat(first.find("tour:920001")).isNull();
        assertThat(second.find("tour:920001")).isNotNull();
        assertThat(second.find("tour:910001")).isNull();
        first.clear();
        assertThat(second.find("tour:920001")).isNotNull();
    }

    private TravelPlanContext context(
            List<String> localFoods,
            List<String> recommendFoods
    ) {

        return context(localFoods, recommendFoods, List.of());
    }

    private TravelPlanContext context(
            List<String> localFoods,
            List<String> recommendFoods,
            List<CreateTravelRequest.PlannedPlaceDetail> plannedPlaces
    ) {

        return new TravelPlanContext(
                new CreateTravelRequest(
                        "선조회 실험",
                        "서울",
                        "종로구",
                        LocalDate.of(2030, 1, 1),
                        DateType.ONE_NIGHT_TWO_DAYS,
                        Transportation.CAR,
                        "서울",
                        plannedPlaces,
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.HISTORY,
                        localFoods,
                        recommendFoods
                ),
                List.of()
        );
    }

    private <T> T fixture(String name, Class<T> type) throws Exception {

        try (var input = getClass().getResourceAsStream("/loadtest/kor2/" + name)) {
            return mapper.readValue(input, type);
        }
    }
}
