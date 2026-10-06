package com.planb.integration.external.foodNtrCpnt;

import com.planb.global.client.foodNtrCpnt.FoodNtrCpntClient;
import com.planb.global.client.foodNtrCpnt.dto.request.FoodNtrCpntSearchRequest;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.client.foodNtrCpnt.handler.FoodNtrCpntHandler;
import com.planb.global.client.foodNtrCpnt.helper.FoodNtrCpntHelper;
import com.planb.global.client.foodNtrCpnt.properties.FoodNtrCpntProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.fail;

@Tag("external")
class FoodNutritionLatencyExternalTest {

    @Test
    @DisplayName("실제 영양 API 순차 호출 응답시간 표본")
    void sequentialResponseLatencySample() throws InterruptedException {

        FoodNtrCpntHandler handler = createHandler();

        List<Double> durations = new ArrayList<>();
        List<String> menus = List.of(
                "비빔밥",
                "불고기",
                "비빔밥"
        );

        for (int index = 0; index < menus.size(); index++) {
            if (index > 0) {
                Thread.sleep(Duration.ofSeconds(2));
            }

            String menu = menus.get(index);
            long started = System.nanoTime();
            FoodNtrCpntResponse response;

            try {
                response = handler
                        .searchFoodNutrition(FoodNtrCpntSearchRequest.of(menu))
                        .block(Duration.ofSeconds(15));
            } catch (RuntimeException failure) {
                double elapsedMillis = (System.nanoTime() - started) / 1_000_000.0;
                String httpStatus = failure instanceof WebClientResponseException httpFailure
                        ? String.valueOf(httpFailure.getStatusCode().value())
                        : "unknown";

                System.out.printf(
                        "PHASE5C_REAL_NUTRITION sample=%d menu=%s elapsed_ms=%.1f outcome=error http_status=%s failure_type=%s%n",
                        index + 1,
                        menu,
                        elapsedMillis,
                        httpStatus,
                        failure.getClass().getSimpleName()
                );

                throw new AssertionError(
                        "영양 API 호출 실패: " + failure.getClass().getSimpleName()
                );
            }

            double elapsedMillis = (System.nanoTime() - started) / 1_000_000.0;
            String apiCode = response == null || response.header() == null
                    ? "missing"
                    : response.header().resultCode();
            boolean itemsPresent = response != null
                    && response.body() != null
                    && response.body().items() != null
                    && !response.body().items().isEmpty();

            System.out.printf(
                    "PHASE5C_REAL_NUTRITION sample=%d menu=%s elapsed_ms=%.1f outcome=%s api_code=%s items_present=%s%n",
                    index + 1,
                    menu,
                    elapsedMillis,
                    "00".equals(apiCode) && itemsPresent ? "success" : "invalid_response",
                    apiCode,
                    itemsPresent
            );

            assertThat(apiCode)
                    .isEqualTo("00");
            assertThat(itemsPresent)
                    .isTrue();

            durations.add(elapsedMillis);
        }

        assertThat(durations)
                .hasSize(3)
                .allSatisfy(duration -> assertThat(duration).isPositive());
    }

    @Test
    @DisplayName("실제 영양 API 동시 2건 응답시간 표본")
    void concurrentPairLatencySample() throws InterruptedException {

        FoodNtrCpntHandler handler = createHandler();

        FoodNtrCpntResponse warmup;

        try {
            warmup = handler
                    .searchFoodNutrition(FoodNtrCpntSearchRequest.of("비빔밥"))
                    .block(Duration.ofSeconds(15));
        } catch (RuntimeException failure) {
            throw new AssertionError(
                    "영양 API 준비 호출 실패: " + failure.getClass().getSimpleName()
            );
        }

        assertThat(warmup)
                .isNotNull();
        assertThat(warmup.header().resultCode())
                .isEqualTo("00");

        Thread.sleep(Duration.ofSeconds(2));

        long started = System.nanoTime();

        List<TimedNutrition> results;

        try {
            results = Flux
                    .fromIterable(List.of(
                            "비빔밥",
                            "불고기"
                    ))
                    .flatMapSequential(
                            menu -> timedLookup(
                                    handler,
                                    menu
                            ),
                            2
                    )
                    .collectList()
                    .block(Duration.ofSeconds(20));
        } catch (RuntimeException failure) {
            throw new AssertionError(
                    "영양 API 동시 호출 실패: " + failure.getClass().getSimpleName()
            );
        }

        double pairMillis = (System.nanoTime() - started) / 1_000_000.0;

        assertThat(results)
                .isNotNull()
                .hasSize(2)
                .extracting(TimedNutrition::menu)
                .containsExactly(
                        "비빔밥",
                        "불고기"
                );

        for (TimedNutrition result : results) {
            String apiCode = result.response().header().resultCode();
            boolean itemsPresent = result.response().body() != null
                    && result.response().body().items() != null
                    && !result.response().body().items().isEmpty();
            int matchedItems = new FoodNtrCpntHelper()
                    .filterFoodNutrition(
                            result.response(),
                            result.menu()
                    )
                    .size();

            System.out.printf(
                    "PHASE5C_REAL_PARALLEL menu=%s elapsed_ms=%.1f api_code=%s items_present=%s matched_items=%d%n",
                    result.menu(),
                    result.elapsedMillis(),
                    apiCode,
                    itemsPresent,
                    matchedItems
            );

            assertThat(apiCode)
                    .isEqualTo("00");
            assertThat(itemsPresent)
                    .isTrue();
        }

        System.out.printf(
                "PHASE5C_REAL_PARALLEL pair_elapsed_ms=%.1f request_count=2%n",
                pairMillis
        );
    }

    private Mono<TimedNutrition> timedLookup(
            FoodNtrCpntHandler handler,
            String menu
    ) {

        return Mono.defer(() -> {
            long started = System.nanoTime();

            return handler
                    .searchFoodNutrition(FoodNtrCpntSearchRequest.of(menu))
                    .map(response -> new TimedNutrition(
                            menu,
                            (System.nanoTime() - started) / 1_000_000.0,
                            response
                    ))
                    .doOnError(failure -> {
                        String httpStatus = failure instanceof WebClientResponseException httpFailure
                                ? String.valueOf(httpFailure.getStatusCode().value())
                                : "unknown";

                        System.out.printf(
                                "PHASE5C_REAL_PARALLEL menu=%s elapsed_ms=%.1f outcome=error http_status=%s failure_type=%s%n",
                                menu,
                                (System.nanoTime() - started) / 1_000_000.0,
                                httpStatus,
                                failure.getClass().getSimpleName()
                        );
                    });
        });
    }

    private FoodNtrCpntHandler createHandler() {

        String baseUrl = requiredEnvironment("FOOD_NTR_CPNT_URL");
        String serviceKey = requiredEnvironment("FOOD_NTR_CPNT_KEY");

        return new FoodNtrCpntHandler(
                new FoodNtrCpntClient(
                        WebClient.builder(),
                        new FoodNtrCpntProperties(
                                baseUrl,
                                serviceKey
                        )
                ),
                new FoodNtrCpntHelper()
        );
    }

    private record TimedNutrition(
            String menu,
            double elapsedMillis,
            FoodNtrCpntResponse response
    ) {
    }

    private String requiredEnvironment(String name) {

        String value = System.getenv(name);

        if (value == null || value.isBlank()) {
            fail("실제 API 표본에 필요한 환경변수 누락: " + name);
        }

        return value;
    }
}
