package com.planb.unit.domain.travel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.planb.ai.client.OpenAiClient;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.CreatePlanSelection;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.ai.handler.MissingSlotCompleter;
import com.planb.ai.handler.PlanGenerationSelectionMapper;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.helper.PlanEditValidator;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.domain.travel.repository.FoodNutritionCacheRepository;
import com.planb.domain.travel.repository.PlanRepository;
import com.planb.domain.travel.service.NutritionService;
import com.planb.domain.travel.service.PlanService;
import com.planb.domain.travel.service.ScheduleNormalizer;
import com.planb.domain.travel.service.TravelMinutesResolver;
import com.planb.global.client.foodNtrCpnt.FoodNtrCpntClient;
import com.planb.global.client.foodNtrCpnt.handler.FoodNtrCpntHandler;
import com.planb.global.client.foodNtrCpnt.helper.FoodNtrCpntHelper;
import com.planb.global.client.foodNtrCpnt.properties.FoodNtrCpntProperties;
import com.planb.global.client.kakaoMapService.KakaoMapServiceClient;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.client.kakaoMapService.helper.KakaoMapRouteHelper;
import com.planb.global.client.kakaoMapService.helper.KakaoPlaceSearchHelper;
import com.planb.global.client.kakaoMapService.helper.KakaoSearchKeywordSanitizer;
import com.planb.global.client.kakaoMapService.properties.KakaoMapServiceProperties;
import com.planb.global.client.kakaoMobilityService.KakaoMobilityServiceClient;
import com.planb.global.client.kakaoMobilityService.helper.KakaoMobilityRouteHelper;
import com.planb.global.client.kakaoMobilityService.properties.KakaoMobilityServiceProperties;
import com.planb.global.client.kor2Service.Kor2ServiceClient;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import com.planb.global.client.kor2Service.properties.Kor2ServiceProperties;
import com.planb.global.config.exception.AiFailure;
import com.planb.global.config.exception.domain.AiOrchestrationException;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.performance.external.ExternalHttpStubServer;
import com.planb.performance.openai.OpenAiChatCompletionStub;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class PlanSelectionRecoveryContractTest {

    private final ObjectMapper mapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(com.fasterxml.jackson.databind.SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final LocalDate date = LocalDate.of(2030, 1, 1);

    private OpenAiChatCompletionStub ai;
    private ExternalHttpStubServer external;
    private PlanService service;
    private boolean lateMealScenario;
    private boolean partialEditScenario;
    private String invalidLateLatitude;
    private int lateDetailCalls;
    private boolean mismatchedLateDetail;
    private boolean duplicateLateMenu;
    private boolean wrongLateCategory;

    @BeforeEach
    void startLocalBoundaries() {

        ai = new OpenAiChatCompletionStub();
        external = ExternalHttpStubServer.start(0, ExternalHttpStubServer.Settings.normal());
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NutritionEvaluationCollector collector = new NutritionEvaluationCollector();
        KakaoMapServiceHandler kakao = new KakaoMapServiceHandler(
                new KakaoMapServiceClient(
                        WebClient.builder(),
                        new KakaoMapServiceProperties(
                                external.baseUrl(ExternalHttpStubServer.Api.KAKAO_MAP),
                                "stub"
                        )
                ),
                new KakaoMobilityServiceClient(
                        WebClient.builder(),
                        new KakaoMobilityServiceProperties(
                                external.baseUrl(ExternalHttpStubServer.Api.KAKAO_MOBILITY),
                                "stub"
                        )
                ),
                new KakaoMapRouteHelper(),
                new KakaoMobilityRouteHelper(),
                new KakaoSearchKeywordSanitizer(),
                new KakaoPlaceSearchHelper(),
                registry
        );
        NutritionService nutrition = new NutritionService(
                new FoodNtrCpntHandler(
                        new FoodNtrCpntClient(
                                WebClient.builder(),
                                new FoodNtrCpntProperties(
                                        external.baseUrl(ExternalHttpStubServer.Api.FOOD_NUTRITION),
                                        "stub"
                                )
                        ),
                        new FoodNtrCpntHelper()
                ),
                mock(FoodNutritionCacheRepository.class),
                registry
        );
        TourismTool tourism = new TourismTool(
                new Kor2ServiceHandler(
                        new Kor2ServiceClient(
                                WebClient
                                        .builder()
                                        .exchangeFunction(this::kor2Boundary),
                                new Kor2ServiceProperties(
                                        external.baseUrl(ExternalHttpStubServer.Api.KOR2),
                                        "stub"
                                ),
                                registry
                        )
                ),
                kakao,
                nutrition,
                collector
        );
        OpenAiChatModel model = OpenAiChatModel
                .builder()
                .options(OpenAiChatOptions
                        .builder()
                        .baseUrl(ai.baseUrl())
                        .apiKey("stub")
                        .model("planb-stub")
                        .maxRetries(0)
                        .timeout(Duration.ofSeconds(5))
                        .build())
                .build();
        TravelRecommendHandler handler = new TravelRecommendHandler(
                new OpenAiClient(ChatClient.create(model), registry),
                mapper,
                new BeanOutputConverter<>(CreatePlanSelection.class),
                new BeanOutputConverter<>(EditPlanAiResponse.class),
                new BeanOutputConverter<>(RebuildPlanDayResponse.class),
                tourism,
                new PlanGenerationSelectionMapper()
        );
        PlanPlaceResolver resolver = new PlanPlaceResolver();
        service = new PlanService(
                mock(PlanRepository.class),
                resolver,
                new PlanEditValidator(),
                new ScheduleNormalizer(resolver),
                handler,
                new TravelMinutesResolver(kakao, 4),
                collector,
                nutrition,
                new MissingSlotCompleter(tourism),
                registry
        );
    }

    @AfterEach
    void stopLocalBoundaries() {

        ai.close();
        external.close();
    }

    @Test
    @DisplayName("실제 파이프라인의 등록 점심 보충과 원본 메뉴 확정")
    void completesMealWithVerifiedSource() throws Exception {

        ai.enqueueFixtures("selection-recovery-tools.json");
        enqueueSelection(List.of());

        CreatePlanAiResponse response = service.makePlanByAi(context());

        assertThat(response
                .planDays()
                .getFirst()
                .schedules())
                .filteredOn(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.candidateId())
                            .isEqualTo("tour:910001");
                    assertThat(slot
                            .restaurantDetail()
                            .menuName())
                            .isEqualTo("비빔밥");
                    assertThat(slot.location())
                            .isEqualTo("스텁시 스텁구 관광로 01");
                    assertThat(slot.longitude())
                            .isEqualTo("126.9750");
                    assertThat(slot.latitude())
                            .isEqualTo("37.5650");
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "181", "NaN"})
    @DisplayName("늦게 보충한 아침의 원본 좌표 누락·범위·형식 오류 거부")
    void rejectsInvalidCoordinatesInLateMealCompletion(String latitude) throws Exception {

        lateMealScenario = true;
        invalidLateLatitude = latitude;
        ai.enqueueFixtures("selection-recovery-tools.json");
        enqueueSelection(List.of(lunch("비빔밥")));

        assertThatThrownBy(() -> service.makePlanByAi(context()))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("좌표");
        assertThat(lateDetailCalls)
                .isEqualTo(2);
    }

    @Test
    @DisplayName("늦은 식사 보충의 유효한 출처와 정상 점심 보존")
    void preservesHealthyMealDuringLateCompletion() throws Exception {

        lateMealScenario = true;
        ai.enqueueFixtures("selection-recovery-tools.json");
        enqueueSelection(List.of(lunch("비빔밥")));

        CreatePlanAiResponse response = service.makePlanByAi(context());

        assertThat(response
                .planDays()
                .getFirst()
                .schedules())
                .filteredOn(slot -> slot.scheduleType() == ScheduleType.BREAKFAST)
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.candidateId())
                            .isEqualTo("tour:910002");
                    assertThat(slot
                            .restaurantDetail()
                            .menuName())
                            .isEqualTo("불고기");
                    assertThat(slot.latitude())
                            .isEqualTo("37.5665");
                });
        assertThat(response
                .planDays()
                .getFirst()
                .schedules())
                .filteredOn(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.candidateId())
                            .isEqualTo("tour:910001");
                    assertThat(slot
                            .restaurantDetail()
                            .menuName())
                            .isEqualTo("비빔밥");
                });
        assertThat(lateDetailCalls)
                .isEqualTo(2);
    }

    @Test
    @DisplayName("초기 메뉴 오류의 슬롯 복구와 뒤쪽 정상 관광지 보존")
    void recoversOnlyInvalidMenuAndPreservesHealthySelections() throws Exception {

        ai.enqueueFixtures("selection-recovery-tools.json");
        enqueueSelection(List.of(
                lunch("출처 없는 메뉴"),
                attraction("tour:900001", 13),
                attraction("tour:900002", 14)
        ));
        ai.enqueueFixtures("selection-recovery-reselect-tools.json");
        ai.enqueueFixture(
                "selection-recovery-content.json",
                Map.of("content", mapper.writeValueAsString(
                        "{\"selectedCandidateId\":\"tour:910003\",\"restaurantDetail\":{\"menuName\":\"칼국수\"}}"
                ))
        );

        CreatePlanAiResponse response = service.makePlanByAi(context());

        assertThat(response
                .planDays()
                .getFirst()
                .schedules())
                .filteredOn(slot -> slot.courseType() == CourseType.ATTRACTION)
                .extracting(CreatePlanAiResponse.PlanScheduleDetail::candidateId)
                .containsExactly("tour:900001", "tour:900002");
        assertThat(response
                .planDays()
                .getFirst()
                .schedules())
                .filteredOn(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.candidateId())
                            .isEqualTo("tour:910003");
                    assertThat(slot
                            .restaurantDetail()
                            .menuName())
                            .isEqualTo("칼국수");
                });
        assertThat(ai.requests())
                .hasSize(4);
    }

    @ParameterizedTest
    @CsvSource({"tour:910003,출처 없는 메뉴", "tour:unknown,칼국수", "tour:900001,칼국수"})
    @DisplayName("유효하지 않은 메뉴·후보의 재선택 두 번 이후 생성 거부")
    void rejectsInvalidReselectionsWithinExistingBudget(
            String candidateId,
            String menu
    ) throws Exception {

        ai.enqueueFixtures("selection-recovery-tools.json");
        enqueueSelection(List.of(lunch("출처 없는 메뉴")));

        for (int attempt = 0; attempt < 2; attempt++) {
            ai.enqueueFixtures("selection-recovery-reselect-tools.json");
            enqueueContent("{\"selectedCandidateId\":\"" + candidateId
                    + "\",\"restaurantDetail\":{\"menuName\":\"" + menu + "\"}}");
        }

        assertThatThrownBy(() -> service.makePlanByAi(context()))
                .isInstanceOf(BaseException.class);
        assertThat(ai.requests())
                .hasSize(6);
    }

    @Test
    @DisplayName("JSON 해석 실패의 기존 응답 단위 재시도와 실패 계약 유지")
    void preservesResponseLevelParsingFailure() {

        ai.enqueueFixtures("parse-failure.json", "parse-failure.json");

        assertThatThrownBy(() -> service.makePlanByAi(context()))
                .isInstanceOf(AiOrchestrationException.class)
                .satisfies(failure -> assertThat(((AiOrchestrationException) failure).getFailure())
                        .isEqualTo(AiFailure.RESPONSE_UNPARSABLE));
        assertThat(ai.requests())
                .hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"source", "duplicate", "category"})
    @DisplayName("출처·메뉴 중복·유형 부적합 후보의 늦은 식사 삽입 제외")
    void excludesUnusableLateMealCandidate(String cause) throws Exception {

        lateMealScenario = true;
        mismatchedLateDetail = cause.equals("source");
        duplicateLateMenu = cause.equals("duplicate");
        wrongLateCategory = cause.equals("category");
        ai.enqueueFixtures("selection-recovery-tools.json");
        enqueueSelection(List.of(lunch("비빔밥")));

        CreatePlanAiResponse response = service.makePlanByAi(context());

        // 당일 첫 아침의 필수 검증 면제, 부적합 후보의 성공 삽입 제외
        assertThat(response
                .planDays()
                .getFirst()
                .schedules())
                .noneMatch(slot -> slot.scheduleType() == ScheduleType.BREAKFAST);
        assertThat(response
                .planDays()
                .getFirst()
                .schedules())
                .filteredOn(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.candidateId())
                            .isEqualTo("tour:910001");
                    assertThat(slot
                            .restaurantDetail()
                            .menuName())
                            .isEqualTo("비빔밥");
                });
        if (!wrongLateCategory) {
            assertThat(lateDetailCalls)
                .isEqualTo(2);
        }
    }

    @Test
    @DisplayName("부분 편집의 늦은 식사 보충에서 보존 날짜 식당·메뉴 예약 유지")
    void reservesUntouchedDayMealsDuringPartialEdit() throws Exception {

        partialEditScenario = true;
        enqueueContent("{\"rebuildDayNumbers\":[1],\"preserveOtherDays\":true,\"densityReductionDayNumbers\":[]}");
        ai.enqueueFixtures("selection-recovery-tools.json");

        EditPlanAiResponse edited = new EditPlanAiResponse(
                "수정 일정",
                List.of(
                        new CreatePlanAiResponse.PlanDayDetail(
                                1,
                                date,
                                List.of(
                                        proposedEditSlot(
                                                ScheduleType.LUNCH,
                                                "tour:910001",
                                                "비빔밥",
                                                12
                                        ),
                                        proposedEditSlot(
                                                ScheduleType.ACTIVITY,
                                                "tour:900001",
                                                null,
                                                13
                                        ),
                                        proposedEditSlot(
                                                ScheduleType.ACTIVITY,
                                                "tour:900002",
                                                null,
                                                14
                                        )
                                )
                        ),
                        new CreatePlanAiResponse.PlanDayDetail(
                                2,
                                date.plusDays(1),
                                List.of()
                        ),
                        new CreatePlanAiResponse.PlanDayDetail(
                                3,
                                date.plusDays(2),
                                List.of()
                        )
                ),
                List.of("1일차 재구성"),
                true
        );
        var tree = mapper.valueToTree(edited);
        for (var day : tree.path("planDays")) {
            for (var node : day.path("schedules")) {
                var slot = (com.fasterxml.jackson.databind.node.ObjectNode) node;
                slot.put(
                        "scheduleType",
                        slot
                                .path("scheduleType")
                                .path("code")
                                .asText()
                );
                slot.put(
                        "courseType",
                        slot
                                .path("courseType")
                                .path("code")
                                .asText()
                );
            }
        }
        enqueueContent(mapper.writeValueAsString(tree));

        GetAiPlanResponse current = new GetAiPlanResponse(
                "기존 일정",
                TravelStyle.LESS_WALK,
                TravelTheme.NATURE,
                List.of(),
                List.of(),
                Set.of(),
                List.of(
                        new GetAiPlanResponse.PlanDayDetail(
                                1,
                                date,
                                List.of(
                                        savedSlot(
                                                ScheduleType.ACTIVITY,
                                                "옛 관광지",
                                                null,
                                                13
                                        )
                                )
                        ),
                        new GetAiPlanResponse.PlanDayDetail(
                                2,
                                date.plusDays(1),
                                List.of(
                                        savedSlot(
                                                ScheduleType.BREAKFAST,
                                                "둘째 날 아침 식당",
                                                "아침 메뉴",
                                                8
                                        ),
                                        savedSlot(
                                                ScheduleType.LUNCH,
                                                "둘째 날 점심 식당",
                                                "점심 메뉴",
                                                12
                                        ),
                                        savedSlot(
                                                ScheduleType.ACTIVITY,
                                                "둘째 날 관광지 A",
                                                null,
                                                13
                                        ),
                                        savedSlot(
                                                ScheduleType.ACTIVITY,
                                                "둘째 날 관광지 B",
                                                null,
                                                14
                                        )
                                )
                        ),
                        new GetAiPlanResponse.PlanDayDetail(
                                3,
                                date.plusDays(2),
                                List.of(
                                        savedSlot(
                                                ScheduleType.BREAKFAST,
                                                "스텁 음식점 02",
                                                "불고기",
                                                8
                                        ),
                                        savedSlot(
                                                ScheduleType.LUNCH,
                                                "셋째 날 점심 식당",
                                                "셋째 메뉴",
                                                12
                                        )
                                )
                        )
                )
        );
        TravelPlanContext generation = context();
        CreateTravelRequest request = generation.createTravelRequest();
        PlanEditContext editContext = new PlanEditContext(
                new CreateTravelRequest(
                        request.travelName(),
                        request.locationDo(),
                        request.locationSigungu(),
                        date,
                        DateType.TWO_NIGHTS_THREE_DAYS,
                        Transportation.CAR,
                        "서울역",
                        List.of(),
                        TravelStyle.LESS_WALK,
                        TravelTheme.NATURE,
                        List.of(),
                        List.of()
                ),
                List.of(new TravelHealthContext(
                        "fixture",
                        List.of(),
                        WalkType.MINIMAL,
                        new TravelHealthContext.MealInfoContext(
                                LocalTime.of(8, 0),
                                LocalTime.of(12, 0),
                                null
                        ),
                        List.of(),
                        List.of()
                )),
                current,
                "1일차만 다른 장소로 재구성"
        );

        EditPlanAiResponse response = service.makeEditPlanByAi(editContext);

        assertThat(response
                .planDays()
                .getFirst()
                .schedules())
                .noneMatch(slot -> "스텁 음식점 02".equals(slot.locationName())
                        || slot.restaurantDetail() != null
                        && "불고기".equals(slot
                                .restaurantDetail()
                                .menuName()));
        assertThat(response
                .planDays()
                .get(2)
                .schedules())
                .extracting(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .containsExactly("스텁 음식점 02", "셋째 날 점심 식당");
        assertThat(ai.requests())
                .hasSize(3);
    }

    private CreatePlanAiResponse.PlanScheduleDetail proposedEditSlot(
            ScheduleType type,
            String candidateId,
            String menu,
            int hour
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                type,
                menu == null ? CourseType.ATTRACTION : CourseType.RESTAURANT,
                LocalTime.of(hour, 0),
                LocalTime.of(hour + 1, 0),
                menu == null
                        ? "tour:900001".equals(candidateId) ? "스텁 관광지 01" : "스텁 관광지 02"
                        : "스텁 음식점 01",
                "AI 주소",
                null,
                null,
                null,
                null,
                60,
                0,
                Set.of(),
                null,
                menu == null ? null : new CreatePlanAiResponse.RestaurantDetail(
                        menu,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                candidateId
        );
    }

    private GetAiPlanResponse.PlanScheduleDetail savedSlot(
            ScheduleType type,
            String name,
            String menu,
            int hour
    ) {

        return new GetAiPlanResponse.PlanScheduleDetail(
                type,
                menu == null ? CourseType.ATTRACTION : CourseType.RESTAURANT,
                LocalTime.of(hour, 0),
                LocalTime.of(hour + 1, 0),
                name,
                "보존 주소",
                "127.1",
                "38.1",
                null,
                null,
                60,
                0,
                Set.of(),
                null,
                menu == null ? null : new GetAiPlanResponse.RestaurantDetail(
                        menu,
                        null,
                        null,
                        null,
                        null,
                        "보존 주소",
                        "127.1",
                        "38.1",
                        null
                )
        );
    }

    private void enqueueContent(String content) throws Exception {

        ai.enqueueFixture(
                "selection-recovery-content.json",
                Map.of("content", mapper.writeValueAsString(content))
        );
    }

    private CreatePlanSelection.ScheduleSelection attraction(
            String candidateId,
            int hour
    ) {

        return new CreatePlanSelection.ScheduleSelection(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(hour, 0),
                LocalTime.of(hour + 1, 0),
                60,
                Set.of(),
                null,
                candidateId
        );
    }

    private Mono<ClientResponse> kor2Boundary(ClientRequest request) {

        String path = request
                .url()
                .getPath();
        String query = request
                .url()
                .getRawQuery();
        String fixture = switch (path.substring(path.lastIndexOf('/') + 1)) {
            case "areaCode2" -> query.contains("areaCode=") ? "sigungu-codes.json" : "area-codes.json";
            case "detailIntro2" -> "detail-intro/" + (query.contains("910003") ? "910003"
                    : query.contains("910002") ? "910002" : "910001") + ".json";
            default -> query.contains("contentTypeId=39") ? "restaurants.json" : "attractions.json";
        };

        if (lateMealScenario && fixture.equals("detail-intro/910002.json") && ++lateDetailCalls == 1) {
            return Mono.just(ClientResponse
                    .create(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("{\"error\":\"fixture unavailable\"}")
                    .build());
        }

        try (var input = getClass().getResourceAsStream("/loadtest/kor2/" + fixture)) {

            String body = new String(
                    input.readAllBytes(),
                    StandardCharsets.UTF_8
            );

            if ((lateMealScenario || partialEditScenario) && fixture.equals("restaurants.json")) {
                var tree = mapper.readTree(body);
                var items = (com.fasterxml.jackson.databind.node.ArrayNode) tree
                        .path("response")
                        .path("body")
                        .path("items")
                        .path("item");
                while (items.size() > 2) {
                    items.remove(items.size() - 1);
                }
                if (wrongLateCategory) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) items.get(1))
                            .put("contenttypeid", "12");
                }
                if (invalidLateLatitude != null) {
                    ((com.fasterxml.jackson.databind.node.ObjectNode) items.get(1))
                            .put("mapy", invalidLateLatitude);
                }
                body = mapper.writeValueAsString(tree);
            }

            if (lateMealScenario && fixture.equals("detail-intro/910002.json")) {
                var tree = mapper.readTree(body);
                var item = (com.fasterxml.jackson.databind.node.ObjectNode) tree
                        .path("response")
                        .path("body")
                        .path("items")
                        .path("item")
                        .get(0);
                if (mismatchedLateDetail) {
                    item.put("contentid", "different-restaurant");
                }
                if (duplicateLateMenu) {
                    item.put("firstmenu", "비빔밥");
                    item.put("treatmenu", "비빔밥");
                }
                body = mapper.writeValueAsString(tree);
            }

            return Mono.just(ClientResponse
                    .create(HttpStatus.OK)
                    .header("Content-Type", "application/json")
                    .body(body)
                    .build());
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    private CreatePlanSelection.ScheduleSelection lunch(String menu) {

        return new CreatePlanSelection.ScheduleSelection(
                ScheduleType.LUNCH,
                CourseType.RESTAURANT,
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                60,
                Set.of(),
                new CreatePlanSelection.RestaurantSelection(menu, null),
                "tour:910001"
        );
    }

    private void enqueueSelection(List<CreatePlanSelection.ScheduleSelection> schedules) throws Exception {

        CreatePlanSelection selection = new CreatePlanSelection(
                List.of(new CreatePlanSelection.PlanDaySelection(
                        1,
                        date,
                        schedules
                ))
        );
        var tree = mapper.valueToTree(selection);
        var slots = tree
                .path("planDays")
                .get(0)
                .path("schedules");

        for (int i = 0; i < schedules.size(); i++) {
            var slot = (com.fasterxml.jackson.databind.node.ObjectNode) slots.get(i);
            slot.put(
                    "scheduleType",
                    schedules
                            .get(i)
                            .scheduleType()
                            .name()
            );
            slot.put(
                    "courseType",
                    schedules
                            .get(i)
                            .courseType()
                            .name()
            );
        }

        ai.enqueueFixture(
                "selection-recovery-content.json",
                Map.of("content", mapper.writeValueAsString(mapper.writeValueAsString(tree)))
        );
    }

    private TravelPlanContext context() {

        return new TravelPlanContext(
                new CreateTravelRequest(
                        "계약 검증",
                        "서울",
                        "종로구",
                        date,
                        DateType.DAY_TRIP,
                        Transportation.CAR,
                        "서울역",
                        List.of(),
                        TravelStyle.LESS_WALK,
                        TravelTheme.NATURE,
                        List.of(),
                        List.of()
                ),
                List.of(new TravelHealthContext(
                        "fixture",
                        List.of(),
                        WalkType.MINIMAL,
                        new TravelHealthContext.MealInfoContext(
                                lateMealScenario ? LocalTime.of(8, 0) : null,
                                LocalTime.of(12, 0),
                                null
                        ),
                        List.of(),
                        List.of()
                ))
        );
    }
}
