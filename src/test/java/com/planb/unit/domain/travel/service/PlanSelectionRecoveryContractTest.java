package com.planb.unit.domain.travel.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.slf4j.LoggerFactory;

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
    private int kor2BoundaryCalls;
    private boolean mealWindowScenario;
    private boolean standardFallbackScenario;
    private boolean standardLookupEmpty;
    private final java.util.List<String> foodLookupNames = new java.util.ArrayList<>();
    private SimpleMeterRegistry registry;
    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(PlanService.class);
    private ListAppender<ILoggingEvent> editLogs;
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
        registry = new SimpleMeterRegistry();
        editLogs = new ListAppender<>();
        editLogs.start();
        serviceLogger.addAppender(editLogs);
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
                        WebClient.builder().exchangeFunction(this::mobilityBoundary),
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
                                WebClient.builder().exchangeFunction(this::nutritionBoundary),
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
                registry,
                new com.planb.domain.travel.service.MealWindowRecoveryService(
                        resolver,
                        new ScheduleNormalizer(resolver),
                        new TravelMinutesResolver(kakao, 4)
                )
        );
    }

    @AfterEach
    void stopLocalBoundaries() {

        ai.close();
        external.close();
        serviceLogger.detachAppender(editLogs);
        editLogs.stop();
    }

    @Test
    @DisplayName("생성 요청의 Tool 반복 중 모델 호출 상한 유지")
    void stopsToolLoopAtRequestBudget() throws Exception {

        ai.enqueueFixtures("selection-recovery-tools.json");
        enqueueSelection(List.of(lunch("비빔밥")));
        TravelPlanContext original = context();
        TravelPlanContext bounded = new TravelPlanContext(
                original.createTravelRequest(),
                original.healthContexts(),
                new com.planb.ai.context.AiCallBudget(1, 24, 128)
        );

        assertThatThrownBy(() -> service.makePlanByAi(bounded))
                .isInstanceOf(AiOrchestrationException.class)
                .satisfies(failure -> assertThat(((AiOrchestrationException) failure).getFailure())
                        .isEqualTo(com.planb.global.config.exception.AiFailure.BUDGET_EXCEEDED));
        assertThat(ai.requests()).hasSize(1);
    }

    @ParameterizedTest
    @CsvSource({"0,128", "24,0", "24,2"})
    @DisplayName("Tool 실행 전 왕복·개수 상한과 추가 모델 요청 차단")
    void stopsToolsBeforeCallbacks(int roundLimit, int toolLimit) throws Exception {

        ai.enqueueFixtures("selection-recovery-tools.json");
        TravelPlanContext original = context();
        TravelPlanContext bounded = new TravelPlanContext(
                original.createTravelRequest(),
                original.healthContexts(),
                new com.planb.ai.context.AiCallBudget(32, roundLimit, toolLimit)
        );

        assertThatThrownBy(() -> service.makePlanByAi(bounded))
                .isInstanceOf(AiOrchestrationException.class)
                .satisfies(failure -> assertThat(((AiOrchestrationException) failure).getFailure())
                        .isEqualTo(com.planb.global.config.exception.AiFailure.BUDGET_EXCEEDED));
        assertThat(ai.requests()).hasSize(1);
        assertThat(external.requests(ExternalHttpStubServer.Api.FOOD_NUTRITION)).isEmpty();
        assertThat(kor2BoundaryCalls).isZero();
    }

    @Test
    @DisplayName("파싱 재시도의 요청 예산 공유와 추가 HTTP 요청 차단")
    void parsingRetryKeepsSameBudget() throws Exception {

        enqueueContent("{");
        TravelPlanContext original = context();
        TravelPlanContext bounded = new TravelPlanContext(
                original.createTravelRequest(),
                original.healthContexts(),
                new com.planb.ai.context.AiCallBudget(1, 24, 128)
        );

        assertThatThrownBy(() -> service.makePlanByAi(bounded))
                .isInstanceOf(AiOrchestrationException.class)
                .satisfies(failure -> assertThat(((AiOrchestrationException) failure).getFailure())
                        .isEqualTo(com.planb.global.config.exception.AiFailure.BUDGET_EXCEEDED));
        assertThat(ai.requests()).hasSize(1);
    }

    @Test
    @DisplayName("두 번째 Tool 왕복의 callback 실행 전 차단")
    void stopsSecondToolRoundBeforeCallbacks() throws Exception {

        ai.enqueueFixtures("selection-recovery-tools.json", "selection-recovery-reselect-tools.json");
        TravelPlanContext original = context();
        TravelPlanContext bounded = new TravelPlanContext(
                original.createTravelRequest(),
                original.healthContexts(),
                new com.planb.ai.context.AiCallBudget(32, 1, 128)
        );

        assertThatThrownBy(() -> service.makePlanByAi(bounded))
                .isInstanceOf(AiOrchestrationException.class)
                .satisfies(failure -> assertThat(((AiOrchestrationException) failure).getFailure())
                        .isEqualTo(com.planb.global.config.exception.AiFailure.BUDGET_EXCEEDED));
        assertThat(ai.requests()).hasSize(2);
        assertThat(kor2BoundaryCalls).isEqualTo(5); // 첫 Tool 3건과 지역 코드 조회 2건
    }

    @Test
    @DisplayName("validation correction의 생성 요청 예산 공유")
    void correctionKeepsSameBudget() throws Exception {

        ai.enqueueFixtures("selection-recovery-tools.json");
        enqueueContent("{\"planDays\":[]}");
        TravelPlanContext original = context();
        TravelPlanContext bounded = new TravelPlanContext(
                original.createTravelRequest(),
                original.healthContexts(),
                new com.planb.ai.context.AiCallBudget(2, 24, 128)
        );

        assertThatThrownBy(() -> service.makePlanByAi(bounded))
                .isInstanceOf(AiOrchestrationException.class)
                .satisfies(failure -> assertThat(((AiOrchestrationException) failure).getFailure())
                        .isEqualTo(com.planb.global.config.exception.AiFailure.BUDGET_EXCEEDED));
        assertThat(ai.requests()).hasSize(2);
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    @DisplayName("Tool 조회 불가 뒤 표준명 보충과 동일 표준 재시도 차단")
    void fillsNutritionAfterUnavailableTool(boolean alreadyTriedStandard) throws Exception {

        standardFallbackScenario = true;
        standardLookupEmpty = alreadyTriedStandard;
        var tools = mapper.readTree(getClass().getResourceAsStream("/loadtest/openai/selection-recovery-tools.json"));
        var calls = (com.fasterxml.jackson.databind.node.ArrayNode) tools
                .path("choices").get(0).path("message").path("tool_calls");
        var call = calls.addObject();
        call.put("id", "nutrition");
        call.put("type", "function");
        call.putObject("function")
                .put("name", "evaluateFoodNutrition")
                .put("arguments", "{\"foodName\":\"특제 비빔밥\",\"standardFoodName\":"
                        + (alreadyTriedStandard ? "\"비빔밥\"" : "null") + ",\"diseaseTypes\":[]}");
        ai.enqueueFixture("selection-recovery-response.json", Map.of("response", mapper.writeValueAsString(tools)));
        var original = lunch("특제 비빔밥");
        enqueueSelection(List.of(new CreatePlanSelection.ScheduleSelection(
                original.scheduleType(), original.courseType(), original.startTime(), original.endTime(),
                original.stayMinutes(), original.tags(),
                new CreatePlanSelection.RestaurantSelection(" 특제 비빔밥 ", "비빔밥"), original.candidateId()
        )));

        var response = service.makePlanByAi(context());
        var restaurant = response.planDays().getFirst().schedules().stream()
                .filter(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .findFirst().orElseThrow().restaurantDetail();
        assertThat(restaurant.menuName()).isEqualTo("특제 비빔밥");

        if (alreadyTriedStandard) {
            assertThat(foodLookupNames).containsExactly("특제 비빔밥", "비빔밥");
            assertThat(restaurant.carbohydrate()).isNull();
        } else {
            assertThat(foodLookupNames).containsExactly("특제 비빔밥", "특제 비빔밥", "비빔밥");
            assertThat(restaurant.carbohydrate()).isEqualTo(20.0);
            assertThat(restaurant.nutritionFoodName()).isEqualTo("비빔밥");
            assertThat(restaurant.nutritionReferenceAmount()).isEqualTo("100g");
            assertThat(restaurant.nutritionSource()).isEqualTo("STANDARD_FOOD_FALLBACK");
        }
    }

    @Test
    @DisplayName("생성 선택의 표준 음식명 보존과 메뉴명 분리")
    void keepsSelectedStandardFoodName() {

        com.planb.ai.context.PlaceCandidateContext candidates = new com.planb.ai.context.PlaceCandidateContext();
        CreatePlanSelection.ScheduleSelection original = lunch("특제 비빔밥");
        CreatePlanSelection.ScheduleSelection selected = new CreatePlanSelection.ScheduleSelection(
                original.scheduleType(),
                original.courseType(),
                original.startTime(),
                original.endTime(),
                original.stayMinutes(),
                original.tags(),
                new CreatePlanSelection.RestaurantSelection("특제 비빔밥", "비빔밥"),
                original.candidateId()
        );
        CreatePlanAiResponse result = new PlanGenerationSelectionMapper().toResponse(
                new CreatePlanSelection(List.of(new CreatePlanSelection.PlanDaySelection(
                        1,
                        date,
                        List.of(selected)
                ))),
                candidates
        );

        assertThat(result.planDays().getFirst().schedules().getFirst().restaurantDetail().menuName())
                .isEqualTo("특제 비빔밥");
        assertThat(candidates.standardFoodName("특제 비빔밥"))
                .isEqualTo("비빔밥");
    }

    @Test
    @DisplayName("생성 finish의 점심 후보 복구 뒤 복약 간격 재산정")
    void generationPipelineRepairsLunchThenRecalculatesMedication() throws Exception {

        mealWindowScenario = true;
        var tools = mapper.readTree(getClass().getResourceAsStream("/loadtest/openai/selection-recovery-tools.json"));
        var calls = (com.fasterxml.jackson.databind.node.ArrayNode) tools.path("choices").get(0).path("message").path("tool_calls");
        var call = calls.addObject();
        call.put("id", "alternateDetail");
        call.put("type", "function");
        call.putObject("function").put("name", "getRestaurantDetail").put("arguments", "{\"contentId\":\"910002\"}");
        var breakfastCall = calls.addObject();
        breakfastCall.put("id", "breakfastDetail");
        breakfastCall.put("type", "function");
        breakfastCall.putObject("function").put("name", "getRestaurantDetail").put("arguments", "{\"contentId\":\"910003\"}");
        ai.enqueueFixture("selection-recovery-response.json", Map.of("response", mapper.writeValueAsString(tools)));
        enqueueSelection(List.of(new CreatePlanSelection.ScheduleSelection(
                ScheduleType.BREAKFAST, CourseType.RESTAURANT, LocalTime.of(8, 0), LocalTime.of(9, 0),
                60, Set.of(), new CreatePlanSelection.RestaurantSelection("칼국수", null), "tour:910003"
        ), attraction("tour:900001", 11), lunch("비빔밥"), attraction("tour:900002", 14)));
        var original = context();
        var health = original.healthContexts().getFirst();
        var rule = new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                com.planb.domain.health.entity.constant.RelatedMeal.LUNCH,
                com.planb.domain.health.entity.constant.MealTiming.AFTER_MEAL,
                30
        );
        var context = new TravelPlanContext(original.createTravelRequest(), List.of(new TravelHealthContext(
                health.travelerName(), health.diseaseTypes(), health.walkType(), health.mealInfo(), health.foodInfos(),
                List.of(new TravelHealthContext.MedicationInfoContext("시험 복약",
                        com.planb.domain.health.entity.constant.MedicationBasis.WITH_MEAL, null, Set.of(rule)))
        )));

        var schedules = service.makePlanByAi(context).planDays().getFirst().schedules();
        var lunch = schedules.stream().filter(slot -> slot.scheduleType() == ScheduleType.LUNCH).findFirst().orElseThrow();
        var medication = schedules.stream().filter(slot -> slot.courseType() == CourseType.MEDICATION).findFirst().orElseThrow();
        assertThat(lunch.candidateId()).isEqualTo("tour:910002");
        assertThat(lunch.startTime()).isBetween(LocalTime.of(11, 30), LocalTime.of(12, 30));
        assertThat(medication.startTime()).isEqualTo(lunch.endTime().plusMinutes(30));
        assertThat(schedules).filteredOn(slot -> slot.courseType() == CourseType.ATTRACTION)
                .extracting(CreatePlanAiResponse.PlanScheduleDetail::candidateId).containsExactly("tour:900001", "tour:900002");
        assertThat(registry.get("planb.travel.plan.stage").tags("flow", "create", "stage", "meal_window_recovery", "outcome", "success").timer().count()).isEqualTo(1);
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

    @Test
    @DisplayName("재선택 candidateId 누락의 검색 원본 유지와 제한 교정")
    void correctsMissingReselectionIdWithoutLosingCandidates() throws Exception {

        ai.enqueueFixtures("selection-recovery-tools.json");
        enqueueSelection(List.of(lunch("출처 없는 메뉴")));
        ai.enqueueFixtures("selection-recovery-reselect-tools.json");
        enqueueContent("{\"selectedCandidateId\":null,\"restaurantDetail\":{\"menuName\":\"칼국수\"}}");
        enqueueContent("{\"selectedCandidateId\":\"tour:910003\",\"restaurantDetail\":{\"menuName\":\"칼국수\"}}");

        CreatePlanAiResponse response = service.makePlanByAi(context());

        assertThat(response.planDays().getFirst().schedules())
                .filteredOn(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .singleElement()
                .satisfies(slot -> assertThat(slot.candidateId()).isEqualTo("tour:910003"));
        assertThat(ai.requests()).hasSize(5);
        assertThat(ai.requests().getLast())
                .contains("등록 후보 JSON", "tour:910003", "칼국수", "menuSourceRecorded");
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
            if ("tour:unknown".equals(candidateId)) {
                enqueueContent("{\"selectedCandidateId\":\"" + candidateId
                        + "\",\"restaurantDetail\":{\"menuName\":\"" + menu + "\"}}");
            }
        }

        assertThatThrownBy(() -> service.makePlanByAi(context()))
                .isInstanceOf(BaseException.class);
        assertThat(ai.requests())
                .hasSize("tour:unknown".equals(candidateId) ? 8 : 6);
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

    @Test
    @DisplayName("실제 AI 호출의 선조회 후보로 메뉴만 변경과 대상 밖 장소 보존")
    void editsMenuUsingPrefetchedRestaurantWithoutToolSearch() throws Exception {

        enqueueContent("{\"rebuildDayNumbers\":[],\"preserveOtherDays\":true,\"densityReductionDayNumbers\":[],"
                + "\"editTargets\":[{\"dayNumber\":1,\"slot\":\"LUNCH\",\"placeName\":null,\"menuOnly\":true}]}");
        EditPlanAiResponse edited = new EditPlanAiResponse(
                "수정 일정",
                List.of(new CreatePlanAiResponse.PlanDayDetail(
                        1,
                        date,
                        List.of(proposedEditSlot(
                                ScheduleType.LUNCH,
                                "tour:910001",
                                "공깃밥",
                                12
                        ))
                )),
                List.of("점심 메뉴 변경"),
                true
        );
        var tree = mapper.valueToTree(edited);
        var slot = (com.fasterxml.jackson.databind.node.ObjectNode) tree
                .path("planDays")
                .get(0)
                .path("schedules")
                .get(0);
        slot.put("scheduleType", "LUNCH");
        slot.put("courseType", "RESTAURANT");
        enqueueContent(mapper.writeValueAsString(tree));

        GetAiPlanResponse.PlanScheduleDetail oldLunch = new GetAiPlanResponse.PlanScheduleDetail(
                ScheduleType.LUNCH,
                CourseType.RESTAURANT,
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                "스텁 음식점 01",
                "서울특별시 종로구 스텁로 01",
                "126.9750",
                "37.5650",
                null,
                null,
                60,
                0,
                Set.of(),
                null,
                new GetAiPlanResponse.RestaurantDetail(
                        "비빔밥",
                        null,
                        null,
                        null,
                        null,
                        "서울특별시 종로구 스텁로 01",
                        "126.9750",
                        "37.5650",
                        null
                )
        );
        GetAiPlanResponse.PlanScheduleDetail untouched = savedSlot(
                ScheduleType.ACTIVITY,
                "보존 관광지",
                null,
                14
        );
        GetAiPlanResponse.PlanScheduleDetail secondUntouched = savedSlot(
                ScheduleType.ACTIVITY,
                "두 번째 보존 관광지",
                null,
                16
        );
        GetAiPlanResponse current = new GetAiPlanResponse(
                "기존 일정",
                TravelStyle.LESS_WALK,
                TravelTheme.NATURE,
                List.of(),
                List.of(),
                Set.of(),
                List.of(new GetAiPlanResponse.PlanDayDetail(
                        1,
                        date,
                        List.of(oldLunch, untouched, secondUntouched)
                ))
        );
        TravelPlanContext generation = context();
        EditPlanAiResponse result = service.makeEditPlanByAi(new PlanEditContext(
                generation.createTravelRequest(),
                generation.healthContexts(),
                current,
                "1일차 점심은 음식점 그대로 두고 메뉴만 다른 것으로 변경"
        ));

        assertThat(ai
                .requests()
                .get(1))
                .contains("tour:910001", "공깃밥");
        assertThat(result
                .planDays()
                .getFirst()
                .schedules())
                .hasSize(3)
                .satisfies(slots -> {
                    assertThat(slots
                            .getFirst()
                            .candidateId())
                            .isEqualTo("tour:910001");
                    assertThat(slots
                            .getFirst()
                            .locationName())
                            .isEqualTo(oldLunch.locationName());
                    assertThat(slots
                            .getFirst()
                            .restaurantDetail()
                            .menuName())
                            .isEqualTo("공깃밥");
                    assertThat(slots.get(1))
                            .usingRecursiveComparison()
                            .ignoringFields("candidateId", "travelMinutes")
                            .isEqualTo(untouched);
                    assertThat(slots
                            .get(1)
                            .travelMinutes())
                            .isEqualTo(15);
                    assertThat(slots.get(2))
                            .usingRecursiveComparison()
                            .ignoringFields("candidateId", "travelMinutes")
                            .isEqualTo(secondUntouched);
                    assertThat(slots
                            .get(2)
                            .travelMinutes())
                            .isEqualTo(15);
                });
        assertThat(ai.requests())
                .hasSize(2);
        assertThat(editLogs.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(message -> assertThat(message)
                        .contains("[PLAN EDIT GUIDE]", "condition=found", "candidateId=tour:910001", "otherMenus=1"))
                .anySatisfy(message -> assertThat(message)
                        .contains("[PLAN EDIT ASSEMBLY]", "source=ai_match", "candidateId=tour:910001", "menu=공깃밥"))
                .anySatisfy(message -> assertThat(message)
                        .contains("[PLAN EDIT FIRST VALIDATION]", "day=1", "slot=LUNCH", "result=pass"))
                .anySatisfy(message -> assertThat(message)
                        .contains("[PLAN EDIT FIRST VALIDATION]", "slot=ACTIVITY", "result=preserved"));

        for (String stage : List.of(
                "scope_classification",
                "candidate_prefetch",
                "ai_response",
                "place_validation",
                "day_rebuild",
                "finish_plan"
        )) {
            assertThat(registry
                    .get("planb.travel.plan.stage")
                    .tags("flow", "edit", "stage", stage, "outcome", "success")
                    .timer()
                    .count())
                    .isEqualTo(1);
        }

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

    private Mono<ClientResponse> mobilityBoundary(ClientRequest request) {

        if (!mealWindowScenario) {
            return org.springframework.web.reactive.function.client.ExchangeFunctions
                    .create(new org.springframework.http.client.reactive.ReactorClientHttpConnector())
                    .exchange(request);
        }
        String query = request.url().getQuery();
        int duration = query.contains("origin=126.97") && query.contains("destination=126.975") ? 21600 : 900;
        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json")
                .body("{\"routes\":[{\"result_code\":0,\"summary\":{\"duration\":" + duration + "}}]}").build());
    }

    private Mono<ClientResponse> nutritionBoundary(ClientRequest request) {

        if (!standardFallbackScenario) {
            return org.springframework.web.reactive.function.client.ExchangeFunctions
                    .create(new org.springframework.http.client.reactive.ReactorClientHttpConnector())
                    .exchange(request);
        }

        String query = java.net.URLDecoder.decode(request.url().getRawQuery(), StandardCharsets.UTF_8);
        String name = query.contains("FOOD_NM_KR=특제 비빔밥") ? "특제 비빔밥" : "비빔밥";
        foodLookupNames.add(name);
        String body = "{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":[]}}";

        if (!standardLookupEmpty && "비빔밥".equals(name)) {
            body = "{\"header\":{\"resultCode\":\"00\"},\"body\":{\"items\":[{"
                    + "\"DB_GRP_NM\":\"음식\",\"FOOD_NM_KR\":\"비빔밥\",\"SERVING_SIZE\":\"100g\",\"AMT_NUM6\":\"20\","
                    + "\"AMT_NUM13\":\"400\",\"AMT_NUM4\":\"5\"}]}}";
        }

        return Mono.just(ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", "application/json").body(body).build());
    }

    private Mono<ClientResponse> kor2Boundary(ClientRequest request) {

        kor2BoundaryCalls++;

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

            if (standardFallbackScenario && fixture.equals("detail-intro/910001.json")) {
                body = body.replace("비빔밥", "특제 비빔밥");
            }

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
                                lateMealScenario || mealWindowScenario ? LocalTime.of(8, 0) : null,
                                LocalTime.of(12, 0),
                                null
                        ),
                        List.of(),
                        List.of()
                ))
        );
    }
}
