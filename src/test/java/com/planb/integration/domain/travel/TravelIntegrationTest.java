package com.planb.integration.domain.travel;

import tools.jackson.databind.ObjectMapper;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.dto.request.AddCompanionRequest;
import com.planb.domain.health.dto.request.MealMedicationRuleDetail;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.response.CreatePlanResponse;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.repository.TravelRepository;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.global.security.dto.request.LoginRequest;
import com.planb.integration.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TravelIntegrationTest extends IntegrationTest {

    /*
    API 호출 URL 모음
     */
    private static final String CREATE_USER_URL =
            "/api/v1/user/create";

    private static final String LOGIN_URL =
            "/login";

    private static final String ADD_COMPANION_URL =
            "/api/v1/health/add-traveler";

    private static final String RECOMMEND_LOCAL_FOOD_URL =
            "/api/v1/travel/recommend-local-food";

    private static final String SEARCH_PLANNED_PLACE_URL =
            "/api/v1/travel/search-planned-place";

    private static final String ADD_WITH_RECOMMEND_URL =
            "/api/v1/travel/add-with-recommend";

    private static final String GET_AI_PLAN_URL =
            "/api/v1/travel/get-ai-travel-plan";

    /*
    테스트 User 정보
     */
    private static final String NICKNAME =
            "travelTestNickname";

    private static final String PASSWORD =
            "test1234!";

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TravelRepository travelRepository;

    @Test
    @DisplayName("실제 OpenAI 및 외부 API 기반 일정 생성 - 컨트롤러 응답(CreatePlanResponse)에는 카페 중복이 없음")
    void makePlanByAiHasNoDuplicateCafe() throws Exception {

        // given
        LocalDate startDate =
                LocalDate.now().plusDays(7);

        CreateTravelRequest createTravelRequest =
                new CreateTravelRequest(
                        "부산 건강 여행",
                        "부산",
                        "해운대구",
                        startDate,
                        DateType.ONE_NIGHT_TWO_DAYS,
                        Transportation.TRANSIT,
                        "해운대",
                        List.of(
                                new CreateTravelRequest.PlannedPlaceDetail(
                                        "해운대해수욕장",
                                        "부산광역시 해운대구"
                                )
                        ),
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of("돼지국밥"),
                        List.of(
                                "돼지국밥",
                                "밀면",
                                "회"
                        )
                );

        String username = createUniqueUsername();
        createUser(username);
        LoginResult loginResult = login(username);
        addCompanion(loginResult.accessToken());

        // when
        CreatePlanResponse response =
                createPlanWithRetry(createTravelRequest, loginResult);

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
                                .param("locationDo", "부산")
                                .param("locationSigungu", "해운대구")
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
                                        .param("searchText", "해운대해수욕장")
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
        addCompanion(loginResult.accessToken());

        String travelName =
                "경주 건강 여행 " + UUID.randomUUID();

        CreateTravelRequest createTravelRequest =
                new CreateTravelRequest(
                        travelName,
                        "경상북도",
                        "경주시",
                        LocalDate.now().plusDays(7),
                        DateType.ONE_NIGHT_TWO_DAYS,
                        Transportation.TRANSIT,
                        "경주",
                        List.of(
                                new CreateTravelRequest.PlannedPlaceDetail(
                                        "불국사",
                                        "경상북도 경주시"
                                )
                        ),
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of("황남빵"),
                        List.of(
                                "황남빵",
                                "쌈밥",
                                "연잎밥"
                        )
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
        mockMvc.perform(
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
                );
    }

    // AI가 간헐적으로 깨진(중복 키) JSON을 반환해 파싱이 실패하는 경우를 흡수하기 위한 재시도
    // makeTravelOptionsAndRecommend는 @Transactional이라 실패 시 저장분이 모두 롤백되므로
    // 같은 요청 재시도해도 중복 데이터 미잔존
    // 파싱 실패 시 ApiExceptionHandler가 success:false로만 응답(HTTP status는 200) → status 아닌 success 기준 판단
    private CreatePlanResponse createPlanWithRetry(
            CreateTravelRequest createTravelRequest,
            LoginResult loginResult) throws Exception {

        int maxAttempts = 3;
        String lastFailureBody = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            MvcResult mvcResult =
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
                            .andReturn();

            String responseBody =
                    mvcResult.getResponse().getContentAsString();

            ApiResultEnvelope result =
                    objectMapper.readValue(responseBody, ApiResultEnvelope.class);

            if (result.success()) {
                return result.data();
            }

            lastFailureBody = responseBody;

            System.out.println(
                    "AI 응답 파싱 실패로 재시도합니다 (" + attempt + "/" + maxAttempts + "). 원인: " + responseBody
            );
        }

        throw new AssertionError(
                "AI 일정 생성이 " + maxAttempts + "회 모두 실패했습니다: " + lastFailureBody
        );
    }

    // /add-with-recommend 응답 바디(ApiResult<CreatePlanResponse>) 역직렬화 전용
    private record ApiResultEnvelope(
            boolean success,
            CreatePlanResponse data
    ) {
    }

    /*
    테스트 회원 생성
     */
    private void createUser(
            String username
    ) throws Exception {

        UserCreateRequest request =
                new UserCreateRequest(
                        username,
                        NICKNAME,
                        PASSWORD,
                        true,
                        true,
                        true
                );

        mockMvc.perform(
                        post(CREATE_USER_URL)
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(
                        status().isCreated()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );
    }

    /*
    로그인 후 AccessToken 및 RefreshToken 반환
     */
    private LoginResult login(
            String username
    ) throws Exception {

        LoginRequest request =
                new LoginRequest(
                        username,
                        PASSWORD
                );

        MvcResult result =
                mockMvc.perform(
                                post(LOGIN_URL)
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        request
                                                )
                                        )
                        )
                        .andExpect(
                                status().isOk()
                        )
                        .andExpect(
                                header().string(
                                        "Authorization",
                                        startsWith("Bearer ")
                                )
                        )
                        .andExpect(
                                cookie().exists(
                                        "refreshToken"
                                )
                        )
                        .andReturn();

        String accessToken =
                result
                        .getResponse()
                        .getHeader(
                                "Authorization"
                        );

        Cookie refreshTokenCookie =
                result
                        .getResponse()
                        .getCookie(
                                "refreshToken"
                        );

        assertThat(accessToken)
                .isNotBlank()
                .startsWith(
                        "Bearer "
                );

        return new LoginResult(
                accessToken,
                refreshTokenCookie
        );
    }

    /*
    동행인(건강정보) 등록 - AI 일정 생성 시 실제 Health 컨텍스트로 반영됨
     */
    private void addCompanion(
            String accessToken
    ) throws Exception {

        AddCompanionRequest request =
                new AddCompanionRequest(
                        "동행인1",
                        true,
                        true,

                        new AddCompanionRequest.HealthInfo(
                                DiseaseType.DIABETES,
                                WalkType.MODERATE
                        ),

                        new AddCompanionRequest.MealInfo(
                                true,

                                true,
                                LocalTime.of(8, 0),

                                true,
                                LocalTime.of(12, 0),

                                true,
                                LocalTime.of(18, 0)
                        ),

                        List.of(
                                new AddCompanionRequest.FoodInfoDetail(
                                        "새우",
                                        FoodType.ALLERGY
                                ),

                                new AddCompanionRequest.FoodInfoDetail(
                                        "과도하게 단 음식",
                                        FoodType.AVOID
                                )
                        ),

                        List.of(
                                new AddCompanionRequest.MedicationInfoDetail(
                                        "테스트 복약",
                                        MedicationBasis.WITH_MEAL,
                                        null,

                                        Set.of(
                                                new MealMedicationRuleDetail(
                                                        RelatedMeal.LUNCH,
                                                        MealTiming.AFTER_MEAL,
                                                        30
                                                )
                                        )
                                )
                        )
                );

        mockMvc.perform(
                        post(ADD_COMPANION_URL)
                                .header(
                                        "Authorization",
                                        accessToken
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(
                        status().isOk()
                )
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );
    }

    private String createUniqueUsername() {

        return "travel-test-"
                + UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                + "@example.com";
    }

    /*
    로그인 결과 내부 DTO
     */
    private record LoginResult(
            String accessToken,
            Cookie refreshTokenCookie
    ) {
    }
}
