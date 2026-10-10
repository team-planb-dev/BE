package com.planb.integration.domain.travel;

import com.planb.domain.health.dto.request.AddCompanionRequest;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.performance.external.ExternalHttpStubServer;
import com.planb.performance.openai.OpenAiChatCompletionStub;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TravelLoadTestSmokeIntegrationTest extends TravelApiTestSupport {

    private static final String ADD_WITH_RECOMMEND_URL =
            "/api/v1/travel/add-with-recommend";

    private static final String GET_AI_PLAN_URL =
            "/api/v1/travel/get-ai-travel-plan";

    private static final LocalDate START_DATE = LocalDate.of(
            2030,
            1,
            1
    );

    private static final long NUTRITION_DELAY_MILLIS = Long.parseLong(System
            .getenv()
            .getOrDefault("PHASE5C_NUTRITION_DELAY_MS", "250"));

    private static final long OPENAI_DELAY_MILLIS = Long.parseLong(System
            .getenv()
            .getOrDefault("PHASE5C_OPENAI_DELAY_MS", "0"));

    private static final ExternalHttpStubServer EXTERNAL_STUB =
            ExternalHttpStubServer.start(
                    0,
                    new ExternalHttpStubServer.Settings(
                            ExternalHttpStubServer.Scenario.NORMAL,
                            Map.of(
                                    ExternalHttpStubServer.Api.FOOD_NUTRITION,
                                    ExternalHttpStubServer.Scenario.DELAY
                            ),
                            Duration.ofMillis(NUTRITION_DELAY_MILLIS),
                            Duration.ofSeconds(30)
                    )
            );

    private static final OpenAiChatCompletionStub OPENAI_STUB =
            OpenAiChatCompletionStub.startTravelPlan(
                    0,
                    START_DATE,
                    START_DATE.plusDays(1),
                    Duration.ofMillis(OPENAI_DELAY_MILLIS)
            );

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private LlmCallRecorder llmCallRecorder;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    // 유료 기준선 실행 전 계측 경로 사전 확인용 호출별 기록기
    @TestConfiguration
    static class RecorderConfig {

        @Bean
        LlmCallRecorder llmCallRecorder() {

            return new LlmCallRecorder();
        }
    }

    @DynamicPropertySource
    static void stubProperties(DynamicPropertyRegistry registry) {

        registry.add(
                "spring.ai.openai.chat.base-url",
                OPENAI_STUB::baseUrl
        );
        registry.add(
                "spring.ai.openai.api-key",
                () -> "planb-stub"
        );
        registry.add(
                "spring.ai.openai.chat.model",
                () -> "planb-stub"
        );
        registry.add(
                "spring.ai.openai.max-retries",
                () -> 0
        );
        registry.add(
                "spring.ai.openai.timeout",
                () -> "10s"
        );

        registerExternalApi(
                registry,
                "external.kor2-service.base-url",
                ExternalHttpStubServer.Api.KOR2
        );
        registerExternalApi(
                registry,
                "external.kakao-map.base-url",
                ExternalHttpStubServer.Api.KAKAO_MAP
        );
        registerExternalApi(
                registry,
                "external.kakao-mobility.base-url",
                ExternalHttpStubServer.Api.KAKAO_MOBILITY
        );
        registerExternalApi(
                registry,
                "external.food-ntr-cpnt.base-url",
                ExternalHttpStubServer.Api.FOOD_NUTRITION
        );

        registry.add(
                "external.kor2-service.service-key",
                () -> "planb-stub"
        );
        registry.add(
                "external.kakao-map.api-key",
                () -> "planb-stub"
        );
        registry.add(
                "external.kakao-mobility.api-key",
                () -> "planb-stub"
        );
        registry.add(
                "external.food-ntr-cpnt.service-key",
                () -> "planb-stub"
        );
    }

    @AfterAll
    static void stopStubs() {

        OPENAI_STUB.close();
        EXTERNAL_STUB.close();
    }

    @Test
    @DisplayName("로컬 외부 스텁 기반 일정 생성과 저장 재조회")
    void createsAndReadsPlanWithLocalStubs() throws Exception {

        long persistenceBefore = stageCount("persistence");
        long nutritionBefore = stageCount("nutrition_enrichment");
        long modelCallsBefore = meterCount("gen_ai.client.operation");
        long toolCallsBefore = meterCount("spring.ai.tool");
        llmCallRecorder.reset();
        int openAiBefore = OPENAI_STUB.requests().size();
        int routeBefore = EXTERNAL_STUB
                .requests(ExternalHttpStubServer.Api.KAKAO_MOBILITY)
                .size();

        String username = createUniqueUsername();
        createUser(username);

        LoginResult login = login(username);
        Long healthId = addMinimalCompanion(login.accessToken());

        CreateTravelRequest request = new CreateTravelRequest(
                "스텁 서울 여행",
                "서울",
                "종로구",
                START_DATE,
                DateType.ONE_NIGHT_TWO_DAYS,
                Transportation.CAR,
                "서울역",
                List.of(),
                TravelStyle.LESS_WALK,
                TravelTheme.NATURE,
                List.of(),
                List.of(),
                List.of(healthId)
        );

        JsonNode created = response(
                post(ADD_WITH_RECOMMEND_URL)
                        .header(
                                "Authorization",
                                login.accessToken()
                        )
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        );

        assertThat(created
                .path("success")
                .asBoolean())
                .isTrue();

        JsonNode createdPlan = created.path("data");
        Long travelId = createdPlan
                .path("travelId")
                .asLong();

        assertThat(travelId)
                .isPositive();
        assertThat(createdPlan
                .path("saved")
                .asBoolean())
                .isFalse();

        assertPlan(createdPlan);
        assertCandidateIds(createdPlan);

        JsonNode persisted = response(
                get(GET_AI_PLAN_URL)
                        .param(
                                "travelId",
                                travelId.toString()
                        )
                        .header(
                                "Authorization",
                                login.accessToken()
                        )
        );

        assertThat(persisted
                .path("success")
                .asBoolean())
                .isTrue();

        assertPlan(persisted.path("data"));

        assertThat(OPENAI_STUB
                .requests())
                .hasSize(openAiBefore + 1);
        assertThat(OPENAI_STUB
                .requests()
                .get(openAiBefore))
                .contains("prefetchedCandidates", "tour:900001");

        assertThat(EXTERNAL_STUB
                .requests(ExternalHttpStubServer.Api.KOR2))
                .extracting(ExternalHttpStubServer.RecordedRequest::path)
                .contains(
                        "/areaCode2",
                        "/areaBasedList2"
                );

        assertThat(EXTERNAL_STUB
                .requests(ExternalHttpStubServer.Api.KAKAO_MOBILITY))
                .hasSize(routeBefore + 4);

        assertThat(stageCount("persistence"))
                .isEqualTo(persistenceBefore + 1);
        assertThat(stageCount("nutrition_enrichment"))
                .isEqualTo(nutritionBefore + 1);

        // 선조회 후보 입력과 검색 Tool 없는 최종 응답 계측
        assertThat(meterCount("gen_ai.client.operation"))
                .isEqualTo(modelCallsBefore + 1);
        assertThat(meterCount("spring.ai.tool"))
                .isEqualTo(toolCallsBefore);
        assertThat(llmCallRecorder.calls())
                .hasSize(1);
        assertThat(llmCallRecorder
                .calls()
                .getFirst()
                .hasToolCalls())
                .isFalse();
        assertThat(llmCallRecorder
                .calls()
                .getLast()
                .hasToolCalls())
                .isFalse();
    }

    private void clearNutritionCache() {

        Set<String> keys = stringRedisTemplate.keys("nutrition:food:*");

        if (keys != null && !keys.isEmpty()) {
            stringRedisTemplate.delete(keys);
        }
    }

    // 태그와 무관한 meter 전체 기록 횟수
    private long meterCount(String name) {

        return meterRegistry
                .find(name)
                .timers()
                .stream()
                .mapToLong(timer -> timer.count())
                .sum();
    }

    @RepeatedTest(3)
    @DisplayName("등록된 점심 메뉴 2개와 지연 영양 조회의 요청 시간 기여")
    void measuresDelayedNutritionLookupsInTravelCreation(
            RepetitionInfo repetitionInfo
    ) throws Exception {

        // 지연 영양 조회 경로 측정용, 이전 회차가 남긴 메뉴명 캐시 제거
        clearNutritionCache();

        long lookupBefore = stageCount("nutrition_lookup");
        double lookupNanosBefore = stageNanos("nutrition_lookup");
        double enrichmentNanosBefore = stageNanos("nutrition_enrichment");
        double orchestrationNanosBefore = orchestrationNanos();
        int foodBefore = EXTERNAL_STUB
                .requests(ExternalHttpStubServer.Api.FOOD_NUTRITION)
                .size();

        String username = createUniqueUsername();
        createUser(username);

        LoginResult login = login(username);
        Long healthId = addLunchCompanion(login.accessToken());

        CreateTravelRequest request = new CreateTravelRequest(
                "영양 지연 스텁 여행",
                "서울",
                "종로구",
                START_DATE,
                DateType.ONE_NIGHT_TWO_DAYS,
                Transportation.CAR,
                "서울역",
                List.of(),
                TravelStyle.LESS_WALK,
                TravelTheme.NATURE,
                List.of(),
                List.of(),
                List.of(healthId)
        );

        long started = System.nanoTime();

        JsonNode created = response(
                post(ADD_WITH_RECOMMEND_URL)
                        .header("Authorization", login.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request))
        );

        long requestNanos = System.nanoTime() - started;
        long lookupCount = stageCount("nutrition_lookup") - lookupBefore;
        double lookupNanos = stageNanos("nutrition_lookup") - lookupNanosBefore;
        double enrichmentNanos = stageNanos("nutrition_enrichment") - enrichmentNanosBefore;
        double orchestrationNanos = orchestrationNanos() - orchestrationNanosBefore;
        int foodCount = EXTERNAL_STUB
                .requests(ExternalHttpStubServer.Api.FOOD_NUTRITION)
                .size() - foodBefore;

        assertThat(created.path("success").asBoolean())
                .isTrue();

        List<String> menus = new ArrayList<>();

        for (JsonNode day : created.path("data").path("planDays")) {
            for (JsonNode schedule : day.path("schedules")) {
                if ("LUNCH".equals(schedule.path("scheduleType").asText())) {
                    menus.add(
                            schedule
                                    .path("restaurantDetail")
                                    .path("menuName")
                                    .asText()
                    );
                }
            }
        }

        assertThat(menus)
                .containsExactly(
                        "비빔밥",
                        "불고기"
                );

        List<ExternalHttpStubServer.RecordedRequest> foodRequests = EXTERNAL_STUB
                .requests(ExternalHttpStubServer.Api.FOOD_NUTRITION);

        assertThat(foodRequests
                .subList(
                        foodBefore,
                        foodRequests.size()
                ))
                .extracting(recorded -> recorded.query().get("FOOD_NM_KR"))
                // 동시 조회로 도착 순서는 계약 아님. 응답 메뉴 순서는 위에서 검증
                .containsExactlyInAnyOrderElementsOf(menus);

        assertThat(lookupCount)
                .isEqualTo(2);
        assertThat(foodCount)
                .isEqualTo(2);
        assertThat(lookupNanos)
                .isGreaterThanOrEqualTo(
                        Duration.ofMillis(NUTRITION_DELAY_MILLIS * lookupCount)
                                .toNanos()
                );
        assertThat(enrichmentNanos)
                .isGreaterThanOrEqualTo(
                        Duration.ofMillis(NUTRITION_DELAY_MILLIS)
                                .toNanos()
                );

        // 두 조회 대기의 겹침. 지연이 짧으면 오차가 커서 판정 생략
        if (NUTRITION_DELAY_MILLIS >= 100) {
            assertThat(enrichmentNanos)
                    .isLessThan(lookupNanos);
        }
        assertThat(orchestrationNanos)
                .isGreaterThanOrEqualTo(enrichmentNanos);
        assertThat((double) requestNanos)
                .isGreaterThanOrEqualTo(orchestrationNanos);

        System.out.printf(
                "PHASE5C_NUTRITION repetition=%d openai_delay_ms=%d nutrition_delay_ms=%d request_ms=%.1f orchestration_ms=%.1f enrichment_ms=%.1f lookup_sum_ms=%.1f lookup_count=%d food_requests=%d%n",
                repetitionInfo.getCurrentRepetition(),
                OPENAI_DELAY_MILLIS,
                NUTRITION_DELAY_MILLIS,
                requestNanos / 1_000_000.0,
                orchestrationNanos / 1_000_000.0,
                enrichmentNanos / 1_000_000.0,
                lookupNanos / 1_000_000.0,
                lookupCount,
                foodCount
        );
    }

    private double stageNanos(String stage) {

        var timer = meterRegistry
                .find("planb.travel.plan.stage")
                .tag("flow", "create")
                .tag("stage", stage)
                .tag("outcome", "success")
                .timer();

        return timer == null ? 0 : timer.totalTime(TimeUnit.NANOSECONDS);
    }

    private double orchestrationNanos() {

        return meterRegistry
                .find("planb.travel.ai.orchestration")
                .tag("days", "2")
                .tag("outcome", "success")
                .timers()
                .stream()
                .mapToDouble(timer -> timer.totalTime(TimeUnit.NANOSECONDS))
                .sum();
    }

    private Long addLunchCompanion(String accessToken) throws Exception {

        String travelerName = "점심 스텁 동행인";

        AddCompanionRequest request = new AddCompanionRequest(
                travelerName,
                true,
                false,
                new AddCompanionRequest.HealthInfo(
                        List.of(DiseaseType.DIABETES),
                        WalkType.MINIMAL
                ),
                new AddCompanionRequest.MealInfo(
                        true,
                        false,
                        null,
                        true,
                        LocalTime.of(12, 0),
                        false,
                        null
                ),
                List.of(),
                List.of()
        );

        mockMvc
                .perform(
                        post("/api/v1/health/add-traveler")
                                .header("Authorization", accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk());

        return findHealthId(accessToken, travelerName);
    }

    private long stageCount(String stage) {

        var timer = meterRegistry
                .find("planb.travel.plan.stage")
                .tag("flow", "create")
                .tag("stage", stage)
                .tag("outcome", "success")
                .timer();

        return timer == null ? 0 : timer.count();
    }

    private void assertCandidateIds(JsonNode plan) {

        for (JsonNode day : plan.path("planDays")) {
            for (JsonNode schedule : day.path("schedules")) {
                assertThat(schedule
                        .path("candidateId")
                        .asText())
                        .startsWith("tour:");
            }
        }
    }

    private Long addMinimalCompanion(String accessToken) throws Exception {

        String travelerName = "스텁 동행인";

        AddCompanionRequest request = new AddCompanionRequest(
                travelerName,
                true,
                false,
                new AddCompanionRequest.HealthInfo(
                        List.of(DiseaseType.DIABETES),
                        WalkType.MINIMAL
                ),
                new AddCompanionRequest.MealInfo(
                        false,
                        false,
                        null,
                        false,
                        null,
                        false,
                        null
                ),
                List.of(),
                List.of()
        );

        mockMvc
                .perform(
                        post("/api/v1/health/add-traveler")
                                .header(
                                        "Authorization",
                                        accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status()
                        .isOk());

        return findHealthId(
                accessToken,
                travelerName
        );
    }

    private JsonNode response(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request
    ) throws Exception {

        String body = mockMvc
                .perform(request)
                .andExpect(status()
                        .isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(body);
    }

    private void assertPlan(JsonNode plan) {

        assertThat(plan
                .path("planDays"))
                .hasSize(2);

        for (JsonNode day : plan.path("planDays")) {
            List<JsonNode> attractions = new ArrayList<>();

            for (JsonNode schedule : day.path("schedules")) {
                if (CourseType.ATTRACTION
                        .name()
                        .equals(schedule
                                .path("courseType")
                                .asText())) {
                    attractions.add(schedule);
                }
            }

            assertThat(attractions)
                    .hasSize(2)
                    .allSatisfy(schedule -> {
                        assertThat(schedule
                                .path("locationName")
                                .asText())
                                .startsWith("스텁 관광지");
                        assertThat(schedule
                                .path("longitude")
                                .asText())
                                .isNotBlank();
                        assertThat(schedule
                                .path("latitude")
                                .asText())
                                .isNotBlank();
                        assertThat(schedule
                                .path("travelMinutes")
                                .asInt())
                                .isEqualTo(15);
                    });
        }
    }

    private static void registerExternalApi(
            DynamicPropertyRegistry registry,
            String property,
            ExternalHttpStubServer.Api api
    ) {

        registry.add(
                property,
                () -> EXTERNAL_STUB.baseUrl(api)
        );
    }
}
