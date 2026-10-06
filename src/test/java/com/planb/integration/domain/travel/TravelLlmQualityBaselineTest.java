package com.planb.integration.domain.travel;

import com.planb.domain.health.dto.request.AddCompanionRequest;
import com.planb.domain.health.dto.request.MealMedicationRuleDetail;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.EditPlanRequest;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 유료 외부 API를 이용하는 externalTest 전용 LLM 품질 기준선 측정
 */
@Tag("external")
class TravelLlmQualityBaselineTest extends TravelApiTestSupport {

    private static final String ADD_WITH_RECOMMEND_URL = "/api/v1/travel/add-with-recommend";
    private static final String ADD_COMPANION_URL = "/api/v1/health/add-traveler";
    private static final String EDIT_PREVIEW_URL = "/api/v1/travel/edit-plan/preview";
    private static final int REPEATS = 3;

    // 실행 전후 태그별 차이를 기록할 meter
    private static final List<String> METERS = List.of(
            "gen_ai.client.operation",
            "gen_ai.client.token.usage",
            "spring.ai.tool",
            "planb.travel.plan.stage",
            "planb.travel.ai.orchestration",
            "planb.ai.retry"
    );

    // 케이스 공통 알레르기(새우) 추정용 키워드, 오탐(해물·짬뽕)과 누락(동의어) 양방향 오차 존재
    private static final List<String> ALLERGEN_KEYWORDS = List.of(
            "새우",
            "대하",
            "쉬림프",
            "감바스",
            "해물",
            "짬뽕",
            "해천"
    );

    // 케이스별 첫 성공 생성에만 실행하는 고정 편집 요청 (관찰용)
    private static final List<String> EDIT_REQUESTS = List.of(
            "2일차 점심 메뉴를 다른 것으로 바꿔줘",
            "2일차 관광지를 전부 다른 곳으로 바꿔줘"
    );

    private static final Set<String> EDITED_CASES = new HashSet<>();

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private LlmCallRecorder llmCallRecorder;

    @Autowired
    private Random attractionCandidateRandom;

    @Autowired
    private Kor2ServiceHandler kor2ServiceHandler;

    // 실험 전용 bean: 호출별 LLM 기록, 관광지 후보 고정 seed
    @TestConfiguration
    static class ExperimentConfig {

        @Bean
        LlmCallRecorder llmCallRecorder() {

            return new LlmCallRecorder();
        }

        @Bean
        Random attractionCandidateRandom() {

            return new Random();
        }
    }

    private static final Path OUTPUT_DIR = Path.of("build", "llm-quality-baseline");
    private static final String RUN_ID = Instant
            .now()
            .toString()
            .replace(":", "-");

    private record GoldenCase(
            String id,
            String locationDo,
            String locationSigungu,
            String decidedLocation,
            String plannedPlace,
            Transportation transportation,
            TravelStyle travelStyle,
            TravelTheme travelTheme,
            List<String> localFoods,
            List<List<DiseaseType>> companions
    ) {

        @Override
        public String toString() {
            return id;
        }
    }

    // TravelPlanAssertions.assertPlan의 2일 일정 검증에 맞춘 전 케이스 1박 2일
    private static final List<GoldenCase> CASES = List.of(
            new GoldenCase(
                    "C1-seoul-single-disease",
                    "서울",
                    "종로구",
                    "서울역",
                    "경복궁",
                    Transportation.TRANSIT,
                    TravelStyle.MATCH_MEAL_TIME,
                    TravelTheme.TASTE,
                    List.of("설렁탕"),
                    List.of(
                            List.of(DiseaseType.DIABETES)
                    )
            ),
            new GoldenCase(
                    "C2-busan-two-companions",
                    "부산",
                    "해운대구",
                    "해운대",
                    "해동용궁사",
                    Transportation.CAR,
                    TravelStyle.LESS_WALK,
                    TravelTheme.NATURE,
                    List.of("돼지국밥"),
                    List.of(
                            List.of(DiseaseType.HIGH_BLOOD_PRESSURE),
                            List.of(DiseaseType.DYSLIPIDEMIA)
                    )
            ),
            new GoldenCase(
                    "C3-gangneung-combined-diseases",
                    "강원특별자치도",
                    "강릉시",
                    "강릉역",
                    "경포대",
                    Transportation.TRANSIT,
                    TravelStyle.LESS_TOURISM,
                    TravelTheme.NATURE,
                    List.of("초당순두부"),
                    List.of(
                            List.of(
                                    DiseaseType.DIABETES,
                                    DiseaseType.HIGH_BLOOD_PRESSURE
                            )
                    )
            ),
            new GoldenCase(
                    "C4-gyeongju-two-companions-combined",
                    "경상북도",
                    "경주시",
                    "경주역",
                    "불국사",
                    Transportation.CAR,
                    TravelStyle.MATCH_MEAL_TIME,
                    TravelTheme.HISTORY,
                    List.of("쌈밥"),
                    List.of(
                            List.of(
                                    DiseaseType.DIABETES,
                                    DiseaseType.DYSLIPIDEMIA
                            ),
                            List.of(DiseaseType.HIGH_BLOOD_PRESSURE)
                    )
            )
    );

    private static final List<String> RECOMMEND_FOODS = List.of(
            "불고기",
            "비빔밥",
            "칼국수",
            "삼계탕",
            "생선구이"
    );

    static Stream<Arguments> runs() {

        return CASES
                .stream()
                .flatMap(goldenCase -> IntStream
                        .rangeClosed(1, REPEATS)
                        .mapToObj(repeat -> Arguments.of(
                                goldenCase,
                                repeat
                        )));
    }

    @ParameterizedTest(name = "{0} #{1}")
    @MethodSource("runs")
    @DisplayName("실제 LLM 품질 기준선 실행")
    void recordBaselineRun(
            GoldenCase goldenCase,
            int repeat
    ) throws Exception {

        // 키 누락 시 전 실행이 AI_TEMPORARILY_UNAVAILABLE로 기록되는 상태
        // 모델 장애로 오인될 가짜 기준선 방지를 위한 키 필수 조건
        assumeTrue(
                System.getenv("OPENAI_API_KEY") != null
                        && !System
                        .getenv("OPENAI_API_KEY")
                        .isBlank(),
                "OPENAI_API_KEY가 없어 기준선 실행을 건너뜀"
        );

        String username = createUniqueUsername();
        createUser(username);
        LoginResult login = login(username);

        List<Long> healthIds = new ArrayList<>();

        for (int index = 0; index < goldenCase
                .companions()
                .size(); index++) {
            healthIds.add(
                    addCompanion(
                            login.accessToken(),
                            "동행인" + (index + 1),
                            goldenCase
                                    .companions()
                                    .get(index)
                    )
            );
        }

        LocalDate startDate = LocalDate
                .now()
                .plusDays(7);

        CreateTravelRequest request = new CreateTravelRequest(
                goldenCase.id(),
                goldenCase.locationDo(),
                goldenCase.locationSigungu(),
                startDate,
                DateType.ONE_NIGHT_TWO_DAYS,
                goldenCase.transportation(),
                goldenCase.decidedLocation(),
                List.of(
                        new CreateTravelRequest.PlannedPlaceDetail(
                                goldenCase.plannedPlace(),
                                goldenCase.locationDo() + " " + goldenCase.locationSigungu()
                        )
                ),
                goldenCase.travelStyle(),
                goldenCase.travelTheme(),
                goldenCase.localFoods(),
                RECOMMEND_FOODS,
                healthIds
        );

        long seed = CASES.indexOf(goldenCase) * 100L + repeat;
        attractionCandidateRandom.setSeed(seed);
        llmCallRecorder.reset();

        MeterSnapshot before = MeterSnapshot.take(
                meterRegistry,
                METERS
        );

        long started = System.nanoTime();

        String body = mockMvc
                .perform(
                        post(ADD_WITH_RECOMMEND_URL)
                                .header(
                                        "Authorization",
                                        login.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andReturn()
                .getResponse()
                .getContentAsString();

        long durationMs = (System.nanoTime() - started) / 1_000_000;

        // 측정 구간 종료 후 계측값 확정, 이후 품질 계수와 편집은 측정 시간 밖
        Map<String, Map<String, Map<String, Double>>> meterDelta = MeterSnapshot
                .take(
                        meterRegistry,
                        METERS
                )
                .deltaSince(before);
        List<LlmCallRecorder.LlmCall> llmCalls = llmCallRecorder.calls();

        String rawFile = goldenCase.id() + "-" + repeat + ".json";
        write(
                OUTPUT_DIR
                        .resolve("raw")
                        .resolve(RUN_ID)
                        .resolve(rawFile),
                body,
                false
        );

        write(
                OUTPUT_DIR.resolve("results-" + RUN_ID + ".jsonl"),
                objectMapper.writeValueAsString(
                        evaluate(
                                goldenCase,
                                repeat,
                                startDate,
                                durationMs,
                                body,
                                rawFile,
                                seed,
                                meterDelta,
                                llmCalls
                        )
                ) + System.lineSeparator(),
                true
        );

        runEdits(
                goldenCase,
                repeat,
                login.accessToken(),
                body
        );
    }

    private record RunResult(
            String runId,
            String caseId,
            int repeat,
            String outcome,
            long durationMs,
            String failure,
            int restaurantCount,
            int nutritionFilledCount,
            String rawFile,
            long seed,
            PlanQualityCounts quality,
            int menuSourceChecked,
            int menuSourceMatched,
            List<LlmCallRecorder.LlmCall> llmCalls,
            Map<String, Map<String, Map<String, Double>>> meterDelta
    ) {
    }

    private record EditResult(
            String runId,
            String caseId,
            int repeat,
            String editRequest,
            String outcome,
            long durationMs,
            String failure,
            boolean unsearchedCandidateFailure,
            int day1ChangedSlots,
            int changeCount,
            int llmCallCount
    ) {
    }

    // 테스트 실패 대신 품질 결과만 기록
    private RunResult evaluate(
            GoldenCase goldenCase,
            int repeat,
            LocalDate startDate,
            long durationMs,
            String body,
            String rawFile,
            long seed,
            Map<String, Map<String, Map<String, Double>>> meterDelta,
            List<LlmCallRecorder.LlmCall> llmCalls
    ) {

        JsonNode root = objectMapper.readTree(body);

        if (!root
                .path("success")
                .asBoolean()) {
            return new RunResult(
                    RUN_ID,
                    goldenCase.id(),
                    repeat,
                    "API_FAILURE",
                    durationMs,
                    summarize(root
                            .path("error")
                            .toString()),
                    0,
                    0,
                    rawFile,
                    seed,
                    null,
                    0,
                    0,
                    llmCalls,
                    meterDelta
            );
        }

        JsonNode plan = root.path("data");

        String outcome = "PASS";
        String failure = null;

        try {
            TravelPlanAssertions.assertPlan(
                    plan,
                    startDate,
                    true
            );
        } catch (AssertionError | RuntimeException error) {
            // 필드 누락 시 assertPlan의 시간 파싱 예외 가능성
            outcome = "INVARIANT_FAILURE";
            failure = summarize(error.toString());
        }

        int restaurants = 0;
        int nutritionFilled = 0;

        for (JsonNode day : plan.path("planDays")) {
            for (JsonNode slot : day.path("schedules")) {
                JsonNode detail = slot.path("restaurantDetail");

                if (detail.isMissingNode() || detail.isNull()) {
                    continue;
                }

                restaurants++;

                if (detail
                        .path("carbohydrate")
                        .isNumber()) {
                    nutritionFilled++;
                }
            }
        }

        int[] menuSource = checkMenuSource(plan);

        return new RunResult(
                RUN_ID,
                goldenCase.id(),
                repeat,
                outcome,
                durationMs,
                failure,
                restaurants,
                nutritionFilled,
                rawFile,
                seed,
                PlanQualityCounts.count(
                        plan,
                        goldenCase.plannedPlace(),
                        ALLERGEN_KEYWORDS
                ),
                menuSource[0],
                menuSource[1],
                llmCalls,
                meterDelta
        );
    }

    // TourAPI 음식점 슬롯의 메뉴가 상세 메뉴에 있는지 재조회, [확인 수, 일치 수]
    private int[] checkMenuSource(JsonNode plan) {

        int checked = 0;
        int matched = 0;

        for (JsonNode day : plan.path("planDays")) {
            for (JsonNode slot : day.path("schedules")) {
                String candidateId = slot
                        .path("candidateId")
                        .asText("");
                JsonNode detail = slot.path("restaurantDetail");

                if (!candidateId.startsWith("tour:") || detail.isMissingNode() || detail.isNull()) {
                    continue;
                }

                Kor2RestaurantIntroResponse intro = kor2ServiceHandler
                        .getRestaurantDetail(candidateId.substring("tour:".length()))
                        .block();

                Kor2RestaurantIntroResponse.Item item = firstItem(intro);

                checked++;

                if (item != null && PlanQualityCounts.menuMatches(
                        detail
                                .path("menuName")
                                .asText(null),
                        item.firstmenu(),
                        item.treatmenu()
                )) {
                    matched++;
                }
            }
        }

        return new int[]{
                checked,
                matched
        };
    }

    private Kor2RestaurantIntroResponse.Item firstItem(Kor2RestaurantIntroResponse intro) {

        try {
            List<Kor2RestaurantIntroResponse.Item> items = intro
                    .response()
                    .body()
                    .items()
                    .item();

            return items == null || items.isEmpty()
                    ? null
                    : items.getFirst();
        } catch (RuntimeException e) {
            return null;
        }
    }

    // 케이스별 첫 성공 생성 결과로 고정 편집 미리보기 2종 실행
    private void runEdits(
            GoldenCase goldenCase,
            int repeat,
            String accessToken,
            String createdBody
    ) throws Exception {

        JsonNode created = objectMapper.readTree(createdBody);

        if (!created
                .path("success")
                .asBoolean() || EDITED_CASES.contains(goldenCase.id())) {
            return;
        }

        EDITED_CASES.add(goldenCase.id());

        long travelId = created
                .path("data")
                .path("travelId")
                .asLong();

        for (String editRequest : EDIT_REQUESTS) {
            llmCallRecorder.reset();

            long started = System.nanoTime();

            String body = mockMvc
                    .perform(
                            post(EDIT_PREVIEW_URL)
                                    .header(
                                            "Authorization",
                                            accessToken
                                    )
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(objectMapper.writeValueAsString(new EditPlanRequest(
                                            travelId,
                                            editRequest
                                    )))
                    )
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            long durationMs = (System.nanoTime() - started) / 1_000_000;

            write(
                    OUTPUT_DIR.resolve("edits-" + RUN_ID + ".jsonl"),
                    objectMapper.writeValueAsString(editResult(
                            goldenCase,
                            repeat,
                            editRequest,
                            durationMs,
                            body,
                            llmCallRecorder
                                    .calls()
                                    .size()
                    )) + System.lineSeparator(),
                    true
            );
        }
    }

    private EditResult editResult(
            GoldenCase goldenCase,
            int repeat,
            String editRequest,
            long durationMs,
            String body,
            int llmCallCount
    ) {

        JsonNode root = objectMapper.readTree(body);

        if (!root
                .path("success")
                .asBoolean()) {
            String failure = summarize(root
                    .path("error")
                    .toString());

            return new EditResult(
                    RUN_ID,
                    goldenCase.id(),
                    repeat,
                    editRequest,
                    "API_FAILURE",
                    durationMs,
                    failure,
                    failure != null && failure.contains("검색하지 않은"),
                    0,
                    0,
                    llmCallCount
            );
        }

        JsonNode data = root.path("data");

        return new EditResult(
                RUN_ID,
                goldenCase.id(),
                repeat,
                editRequest,
                "SUCCESS",
                durationMs,
                null,
                false,
                changedSlots(
                        data
                                .path("before")
                                .path("planDays")
                                .path(0),
                        data
                                .path("after")
                                .path("planDays")
                                .path(0)
                ),
                data
                        .path("after")
                        .path("changes")
                        .size(),
                llmCallCount
        );
    }

    // 요청 대상이 아닌 1일차의 장소 변경 수, 위치별 장소명 비교와 길이 차이 합산
    private int changedSlots(
            JsonNode before,
            JsonNode after
    ) {

        List<String> beforeNames = new ArrayList<>();
        List<String> afterNames = new ArrayList<>();

        before
                .path("schedules")
                .forEach(slot -> beforeNames.add(slot
                        .path("locationName")
                        .asText("")));
        after
                .path("schedules")
                .forEach(slot -> afterNames.add(slot
                        .path("locationName")
                        .asText("")));

        int changed = Math.abs(beforeNames.size() - afterNames.size());

        for (int index = 0; index < Math.min(beforeNames.size(), afterNames.size()); index++) {
            if (!beforeNames
                    .get(index)
                    .equals(afterNames.get(index))) {
                changed++;
            }
        }

        return changed;
    }

    // 계획 원문을 제외한 실패 사유 첫 줄만 기록
    private String summarize(String message) {

        if (message == null) {
            return null;
        }

        String firstLine = message
                .strip()
                .lines()
                .findFirst()
                .orElse("");

        return firstLine.length() > 200
                ? firstLine.substring(0, 200)
                : firstLine;
    }

    private void write(
            Path path,
            String content,
            boolean append
    ) throws IOException {

        Files.createDirectories(path.getParent());

        Files.writeString(
                path,
                content,
                StandardOpenOption.CREATE,
                append
                        ? StandardOpenOption.APPEND
                        : StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    // 케이스별 질환만 변경, 식사·알레르기·복약 조건 유지
    private Long addCompanion(
            String accessToken,
            String travelerName,
            List<DiseaseType> diseaseTypes
    ) throws Exception {

        AddCompanionRequest request = new AddCompanionRequest(
                travelerName,
                true,
                true,
                new AddCompanionRequest.HealthInfo(
                        diseaseTypes,
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

        mockMvc
                .perform(
                        post(ADD_COMPANION_URL)
                                .header(
                                        "Authorization",
                                        accessToken
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status()
                        .isOk())
                .andExpect(jsonPath("$.success")
                        .value(true));

        return findHealthId(
                accessToken,
                travelerName
        );
    }
}
