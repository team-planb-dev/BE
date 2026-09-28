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
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TravelLoadTestSmokeIntegrationTest extends TravelApiTestSupport {

    private static final String ADD_WITH_RECOMMEND_URL =
            "/api/v1/travel/add-with-recommend";

    private static final String GET_AI_PLAN_URL =
            "/api/v1/travel/get-ai-travel-plan";

    private static final ExternalHttpStubServer EXTERNAL_STUB =
            ExternalHttpStubServer.start(
                    0,
                    ExternalHttpStubServer.Settings.normal()
            );

    private static final OpenAiChatCompletionStub OPENAI_STUB =
            new OpenAiChatCompletionStub();

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
                () -> "2s"
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

        LocalDate startDate = LocalDate
                .now()
                .plusDays(7);

        OPENAI_STUB.enqueueFixture(
                "travel-attraction-tool-call.json",
                Map.of()
        );
        OPENAI_STUB.enqueueFixture(
                "travel-empty-plan.json",
                Map.of(
                        "startDate", startDate.toString(),
                        "endDate", startDate
                                .plusDays(1)
                                .toString()
                )
        );

        String username = createUniqueUsername();
        createUser(username);

        LoginResult login = login(username);
        Long healthId = addMinimalCompanion(login.accessToken());

        CreateTravelRequest request = new CreateTravelRequest(
                "스텁 서울 여행",
                "서울",
                "종로구",
                startDate,
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
                .hasSize(2);
        assertThat(OPENAI_STUB
                .requests()
                .get(1))
                .contains("tour:900001");

        assertThat(EXTERNAL_STUB
                .requests(ExternalHttpStubServer.Api.KOR2))
                .extracting(ExternalHttpStubServer.RecordedRequest::path)
                .contains(
                        "/areaCode2",
                        "/areaBasedList2"
                );

        assertThat(EXTERNAL_STUB
                .requests(ExternalHttpStubServer.Api.KAKAO_MOBILITY))
                .hasSize(4);
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
                .andExpect(status().isOk());

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
                .andExpect(status().isOk())
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
