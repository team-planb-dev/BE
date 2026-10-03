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
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
import java.util.List;
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
    private static final int REPEATS = 3;

    private static final Path OUTPUT_DIR = Path.of("build", "llm-quality-baseline");
    private static final String RUN_ID = Instant.now()
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

    // 모든 케이스는 1박 2일이다. TravelPlanAssertions.assertPlan이 2일 일정을 전제로 검사한다.
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

        // 키가 없어도 애플리케이션은 기동되고 모든 실행이 AI_TEMPORARILY_UNAVAILABLE로 기록된다.
        // 모델 장애처럼 보이는 가짜 기준선이 남지 않도록 키가 없으면 실행하지 않는다.
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

        for (int index = 0; index < goldenCase.companions().size(); index++) {
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
                                rawFile
                        )
                ) + System.lineSeparator(),
                true
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
            String rawFile
    ) {
    }

    // 품질 결과로 테스트를 실패시키지 않고 결과만 기록한다.
    private RunResult evaluate(
            GoldenCase goldenCase,
            int repeat,
            LocalDate startDate,
            long durationMs,
            String body,
            String rawFile
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
                    rawFile
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
            // 필드 누락 시 assertPlan 안의 시간 파싱이 AssertionError 대신 예외를 던질 수 있다.
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

        return new RunResult(
                RUN_ID,
                goldenCase.id(),
                repeat,
                outcome,
                durationMs,
                failure,
                restaurants,
                nutritionFilled,
                rawFile
        );
    }

    // 실패 메시지는 원인 식별용 첫 줄만 남긴다. 계획 원문 전체를 결과 행에 넣지 않는다.
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

    // 질환만 케이스별로 바꾸고 식사·알레르기·복약 조건은 기존 동행인 등록과 같게 둔다.
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
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        return findHealthId(
                accessToken,
                travelerName
        );
    }
}
