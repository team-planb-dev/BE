package com.planb.integration.domain.travel;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.response.CreatePlanResponse;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.repository.TravelRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import tools.jackson.databind.JsonNode;
import com.planb.domain.travel.dto.request.EditPlanRequest;
import com.planb.domain.travel.dto.request.GetAiPlanRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TravelIntegrationTest extends TravelApiTestSupport {

    /*
    API 호출 URL 모음
     */
    private static final String RECOMMEND_LOCAL_FOOD_URL =
            "/api/v1/travel/recommend-local-food";

    private static final String SEARCH_PLANNED_PLACE_URL =
            "/api/v1/travel/search-planned-place";

    private static final String ADD_WITH_RECOMMEND_URL =
            "/api/v1/travel/add-with-recommend";

    private static final String GET_AI_PLAN_URL =
            "/api/v1/travel/get-ai-travel-plan";

    @Autowired
    private TravelRepository travelRepository;

    @Test
    @DisplayName("실제 OpenAI 및 외부 API 기반 일정 생성 - 컨트롤러 응답(CreatePlanResponse)에는 카페 중복이 없음")
    void makePlanByAiHasNoDuplicateCafe() throws Exception {

        // given
        LocalDate startDate =
                LocalDate.now().plusDays(7);

        String username = createUniqueUsername();
        createUser(username);
        LoginResult loginResult = login(username);
        Long healthId = addCompanion(loginResult.accessToken());

        CreateTravelRequest createTravelRequest =
                new CreateTravelRequest(
                        "서울 건강 여행",
                        "서울",
                        "종로구",
                        startDate,
                        DateType.ONE_NIGHT_TWO_DAYS,
                        Transportation.TRANSIT,
                        "서울역",
                        List.of(
                                new CreateTravelRequest.PlannedPlaceDetail(
                                        "경복궁",
                                        "서울 종로구"
                                )
                        ),
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of("설렁탕"),
                        List.of(
                                "불고기",
                                "비빔밥",
                                "칼국수",
                                "삼계탕",
                                "생선구이"
                        ),
                        List.of(healthId)
                );

        // when
        CreatePlanResponse response =
                createPlanOnce(createTravelRequest, loginResult);

        // then
        System.out.println(
                "===== TravelController.addTravelOptionsAndRecommend (카페 중복 보정 후) 응답 ====="
        );

        System.out.println(
                objectMapper
                        .writerWithDefaultPrettyPrinter()
                        .writeValueAsString(response)
        );

        assertThat(response)
                .isNotNull();

        assertThat(response.planDays())
                .isNotNull()
                .hasSize(2);

        List<String> cafeLocationNames =
                response.planDays().stream()
                        .flatMap(planDay -> planDay.schedules().stream())
                        .filter(schedule -> schedule.courseType() == CourseType.CAFE_REST)
                        .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                        .toList();

        assertThat(cafeLocationNames)
                .doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("회원가입 및 로그인 후 지역 음식 추천 성공")
    void recommendLocalFoodSuccess() throws Exception {

        // given
        String username = createUniqueUsername();
        createUser(username);
        LoginResult loginResult = login(username);

        // when & then
        mockMvc.perform(
                        get(RECOMMEND_LOCAL_FOOD_URL)
                                .param("locationDo", "제주특별자치도")
                                .param("locationSigungu", "제주시")
                                .header(
                                        "Authorization",
                                        loginResult.accessToken()
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.foods")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.data.foods")
                                .isNotEmpty()
                );
    }

    @Test
    @DisplayName("회원가입 및 로그인 후 계획 장소 검색 성공")
    void searchPlannedPlaceSuccess() throws Exception {

        // given
        String username = createUniqueUsername();
        createUser(username);
        LoginResult loginResult = login(username);

        // when
        MvcResult mvcResult =
                mockMvc.perform(
                                get(SEARCH_PLANNED_PLACE_URL)
                                        .param("searchText", "전주한옥마을")
                                        .header(
                                                "Authorization",
                                                loginResult.accessToken()
                                        )
                        )
                        .andExpect(
                                request().asyncStarted()
                        )
                        .andReturn();

        // then
        mockMvc.perform(
                        asyncDispatch(mvcResult)
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.plannedPlaces")
                                .isArray()
                );
    }

    @Test
    @DisplayName("회원가입, 로그인, 동행인 등록 후 AI 일정 생성 및 재조회 성공 - 최상위 tags가 저장/재조회까지 일치함")
    void addTravelOptionsAndRecommendThenGetAiPlanSuccess() throws Exception {

        // given
        String username = createUniqueUsername();
        createUser(username);
        LoginResult loginResult = login(username);
        Long healthId = addCompanion(loginResult.accessToken());

        String travelName =
                "강릉 건강 여행 " + UUID.randomUUID();

        CreateTravelRequest createTravelRequest =
                new CreateTravelRequest(
                        travelName,
                        "강원특별자치도",
                        "강릉시",
                        LocalDate.now().plusDays(7),
                        DateType.ONE_NIGHT_TWO_DAYS,
                        Transportation.TRANSIT,
                        "강릉역",
                        List.of(
                                new CreateTravelRequest.PlannedPlaceDetail(
                                        "경포대",
                                        "강원특별자치도 강릉시"
                                )
                        ),
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of("초당순두부"),
                        List.of(
                                "물회",
                                "장칼국수",
                                "감자옹심이",
                                "생선구이",
                                "막국수"
                        ),
                        List.of(healthId)
                );

        // when : AI 일정 생성 (실제 OpenAI + 실제 외부 API + 실제 DB 저장까지 전부 연결)
        MvcResult createResult =
                mockMvc.perform(
                                post(ADD_WITH_RECOMMEND_URL)
                                        .header(
                                                "Authorization",
                                                loginResult.accessToken()
                                        )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        createTravelRequest
                                                )
                                        )
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                jsonPath("$.success")
                                        .value(true)
                        )
                        .andExpect(
                                jsonPath("$.data.tags")
                                        .isArray()
                        )
                        .andExpect(
                                jsonPath("$.data.planDays")
                                        .isArray()
                        )
                        .andExpect(
                                jsonPath("$.data.planDays")
                                        .isNotEmpty()
                        )
                        .andReturn();

        String createResponseBody =
                createResult
                        .getResponse()
                        .getContentAsString();

        int tagsCountFromCreate =
                objectMapper
                        .readTree(createResponseBody)
                        .get("data")
                        .get("tags")
                        .size();

        // 방금 생성된 Travel의 travelId 조회 (CreatePlanResponse는 travelId를 반환하지 않으므로 DB에서 직접 조회)
        Travel travel =
                travelRepository.findAll().stream()
                        .filter(t -> t.getTravelName().equals(travelName))
                        .findFirst()
                        .orElseThrow();

        // then : 방금 생성한 일정 재조회 시 최상위 tags 생성 시점과 동일하게 저장/재조회 확인
        MvcResult retrieved = mockMvc.perform(
                        get(GET_AI_PLAN_URL)
                                .param(
                                        "travelId",
                                        String.valueOf(travel.getId())
                                )
                                .header(
                                        "Authorization",
                                        loginResult.accessToken()
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                )
                .andExpect(
                        jsonPath("$.data.planName")
                                .value(travelName)
                )
                .andExpect(
                        jsonPath("$.data.tags")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.data.tags.length()")
                                .value(tagsCountFromCreate)
                )
                .andExpect(
                        jsonPath("$.data.planDays")
                                .isArray()
                )
                .andExpect(
                        jsonPath("$.data.planDays")
                                .isNotEmpty()
                )
                .andReturn();

        JsonNode created = objectMapper
                .readTree(createResponseBody)
                .path("data");

        JsonNode stored = objectMapper
                .readTree(retrieved.getResponse()
                        .getContentAsString())
                .path("data");

        TravelPlanAssertions.assertPlan(created, createTravelRequest.startDate(), true);
        TravelPlanAssertions.assertPlan(stored, createTravelRequest.startDate(), false);
        TravelPlanAssertions.assertMealMedication(
                stored,
                LocalTime.of(12, 0)
        );
        TravelPlanAssertions.assertSameDays(created.path("planDays"), stored.path("planDays"));
        assertThat(TravelPlanAssertions.codes(stored.path("tags")))
                .isEqualTo(TravelPlanAssertions.codes(created.path("tags")));

        verifyEditLifecycle(travel.getId(), loginResult, stored, createTravelRequest.startDate());
    }

    // 실제 수정 미리보기·확정·재조회 검증
    private void verifyEditLifecycle(
            Long travelId,
            LoginResult login,
            JsonNode original,
            LocalDate startDate
    ) throws Exception {

        JsonNode preview = editRequest(
                "/edit-plan/preview",
                new EditPlanRequest(travelId, "1일차 일정을 관광지 위주로 통째로 다시 짜주세요. 2일차는 유지해주세요."),
                login);

        JsonNode after = preview.path("data")
                .path("after");
        assertThat(after.path("processable")
                .asBoolean())
                .isTrue();
        TravelPlanAssertions.assertPlan(after, startDate, true);
        TravelPlanAssertions.assertMealMedication(
                after,
                LocalTime.of(12, 0)
        );
        TravelPlanAssertions.assertSameDays(original.path("planDays"), getStored(travelId, login)
                .path("planDays"));
        assertThat(after.path("planDays")
                .get(1))
                .isNotNull();
        TravelPlanAssertions.assertSameDaysIgnoringInboundTravel(
                objectMapper.valueToTree(List.of(original.path("planDays")
                        .get(1))),
                objectMapper.valueToTree(List.of(after.path("planDays")
                        .get(1))));

        JsonNode confirmed = editRequest(
                "/edit-plan/confirm",
                new GetAiPlanRequest(travelId),
                login)
                        .path("data");

        TravelPlanAssertions.assertSameDays(after.path("planDays"), confirmed.path("planDays"));
        TravelPlanAssertions.assertSameDays(after.path("planDays"), getStored(travelId, login)
                .path("planDays"));
        TravelPlanAssertions.assertPlan(confirmed, startDate, true);

    }

    private JsonNode editRequest(
            String path,
            Object payload,
            LoginResult login
    ) throws Exception {

        MvcResult result = mockMvc
                .perform(
                        post("/api/v1/travel" + path)
                                .header("Authorization", login.accessToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status()
                        .isOk())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andReturn();

        return objectMapper.readTree(result.getResponse()
                .getContentAsString());
    }

    private JsonNode getStored(Long travelId, LoginResult login) throws Exception {

        MvcResult result = mockMvc
                .perform(
                        get(GET_AI_PLAN_URL)
                                .param("travelId", travelId.toString())
                                .header("Authorization", login.accessToken()))
                .andExpect(status()
                        .isOk())
                .andExpect(jsonPath("$.success")
                        .value(true))
                .andReturn();

        return objectMapper.readTree(result.getResponse()
                .getContentAsString())
                .path("data");
    }

    // 실제 API 실패 응답을 재시도 없이 그대로 검증
    private CreatePlanResponse createPlanOnce(
            CreateTravelRequest createTravelRequest,
            LoginResult loginResult
    ) throws Exception {

        MvcResult result = mockMvc
                .perform(
                        post(ADD_WITH_RECOMMEND_URL)
                                .header("Authorization", loginResult.accessToken())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(createTravelRequest)))
                .andExpect(status()
                        .isOk())
                .andReturn();

        String body = result.getResponse()
                .getContentAsString();
        ApiResultEnvelope response = objectMapper.readValue(body, ApiResultEnvelope.class);

        assertThat(response.success())
                .withFailMessage(body)
                .isTrue();

        TravelPlanAssertions.assertPlan(
                objectMapper.readTree(body)
                        .path("data"),
                createTravelRequest.startDate(),
                true);

        return response.data();
    }

    // /add-with-recommend 응답 바디(ApiResult<CreatePlanResponse>) 역직렬화 전용
    private record ApiResultEnvelope(
            boolean success,
            CreatePlanResponse data
    ) {
    }

}
