package com.planb.unit.ai.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.client.OpenAiClient;
import com.planb.ai.context.PlaceCandidateContext;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.dto.response.PlaceReselectResponse;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.mcp.TourismTool;
import com.planb.ai.prompt.AiPrompt;
import com.planb.ai.prompt.PlaceReselectPrompt;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.converter.BeanOutputConverter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TravelRecommendHandlerContractTest {

    @Mock
    private OpenAiClient openAiClient;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private BeanOutputConverter<CreatePlanAiResponse> createPlanAiResponseConverter;

    @Mock
    private BeanOutputConverter<EditPlanAiResponse> editPlanAiResponseConverter;

    @Mock
    private BeanOutputConverter<RebuildPlanDayResponse> rebuildPlanDayResponseConverter;

    @Mock
    private TourismTool tourismTool;

    @Mock
    private PlaceReselectPrompt prompt;

    @BeforeEach
    void originalSlot() {

        lenient()
                .when(prompt.slot())
                .thenReturn(slot());
    }

    @Test
    @DisplayName("ACTIVE 일정의 관광지 부족 날짜와 개수를 교정 사유로 전달")
    void describesActiveTouristPlaceCountFailuresForCorrection() {

        TravelPlanContext context = travelPlanContext(WalkType.ACTIVE);

        handler()
                .createPlanByAi(
                        context,
                        new PlaceCandidateContext()
        );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<CreatePlanAiResponse, List<String>>> validation =
                ArgumentCaptor.forClass(Function.class);

        verify(openAiClient)
                .call(
                        any(AiPrompt.class),
                        eq(createPlanAiResponseConverter),
                        validation.capture(),
                        any(Object[].class)
                );

        List<String> failures = validation
                .getValue()
                .apply(planWithAttractions(1, 0));

        assertNotNull(failures);
        assertTrue(failures.stream().anyMatch(failure ->
                failure.contains("day1].schedules: 관광지 3개 필요 / 실제 1개 / 추가 2개")));
        assertTrue(failures.stream().anyMatch(failure ->
                failure.contains("유지 candidateId [tour:0-0]")));
        assertTrue(failures.stream().anyMatch(failure ->
                failure.contains("day2].schedules: 관광지 3개 필요 / 실제 0개 / 추가 3개")));
        assertTrue(failures.stream().anyMatch(failure ->
                failure.contains("유지 candidateId []")));
        assertTrue(failures.stream().allMatch(failure ->
                failure.contains("최초 관광지 candidate 목록 안에서 교정")));

        List<String> combinedFailures = validation
                .getValue()
                .apply(planWithAttractions(1, 0, 3));

        assertTrue(combinedFailures.stream().anyMatch(failure ->
                failure.contains("여행 일수 2일 필요 / 실제 3일")));
        assertTrue(combinedFailures.stream().anyMatch(failure ->
                failure.contains("day1].schedules: 관광지 3개 필요 / 실제 1개")));
        assertTrue(combinedFailures.stream().anyMatch(failure ->
                failure.contains("day2].schedules: 관광지 3개 필요 / 실제 0개")));
        assertTrue(validation.getValue().apply(planWithAttractions(3, 3)).isEmpty());
    }

    @Test
    @DisplayName("음식점 후보를 찾은 일정의 식사 슬롯 누락을 교정 사유로 전달")
    void describesMissingMealSlotWhenRestaurantCandidateExists() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(restaurantItem());

        handler()
                .createPlanByAi(
                        mealAppliedContext(),
                        candidates
                );

        List<String> failures = capturedPlanValidation()
                .apply(planWithAttractions(3, 3));

        assertTrue(failures.stream().anyMatch(failure ->
                failure.contains("day1].schedules: LUNCH 식사 슬롯 필요")));
        assertTrue(failures.stream().anyMatch(failure ->
                failure.contains("day2].schedules: LUNCH 식사 슬롯 필요")));
    }

    @Test
    @DisplayName("음식점 후보를 찾지 못한 일정의 식사 슬롯 미요구")
    void allowsMissingMealSlotWhenNoRestaurantCandidate() {

        handler()
                .createPlanByAi(
                        mealAppliedContext(),
                        new PlaceCandidateContext()
                );

        assertTrue(
                capturedPlanValidation()
                        .apply(planWithAttractions(3, 3))
                        .isEmpty()
        );
    }

    private Function<CreatePlanAiResponse, List<String>> capturedPlanValidation() {

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<CreatePlanAiResponse, List<String>>> validation =
                ArgumentCaptor.forClass(Function.class);

        verify(openAiClient)
                .call(
                        any(AiPrompt.class),
                        eq(createPlanAiResponseConverter),
                        validation.capture(),
                        any(Object[].class)
                );

        return validation.getValue();
    }

    private TravelPlanContext mealAppliedContext() {

        TravelPlanContext context = travelPlanContext(WalkType.ACTIVE);

        return new TravelPlanContext(
                context.createTravelRequest(),
                List.of(
                        new TravelHealthContext(
                                "여행자",
                                DiseaseType.DIABETES,
                                WalkType.ACTIVE,
                                new TravelHealthContext.MealInfoContext(
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
                        )
                )
        );
    }

    private Kor2KeywordSearchResponse.Item restaurantItem() {

        return new Kor2KeywordSearchResponse.Item(
                "서울특별시 종로구",
                "",
                null,
                "134712",
                "39",
                null,
                null,
                null,
                null,
                "126.9",
                "37.5",
                null,
                null,
                null,
                "토속촌삼계탕",
                null,
                null,
                null,
                null,
                null
        );
    }

    @Test
    @DisplayName("처리 가능한 수정 응답의 빈 변경 내역 교정")
    void describesMissingChangesForProcessableEditResponse() {

        PlanEditContext context = new PlanEditContext(
                travelPlanContext(WalkType.ACTIVE)
                        .createTravelRequest(),
                List.of(),
                null,
                "식사 메뉴를 바꿔주세요."
        );

        handler()
                .editPlanByAi(
                        context,
                        new PlaceCandidateContext()
                );

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Function<EditPlanAiResponse, List<String>>> validation =
                ArgumentCaptor.forClass(Function.class);

        verify(openAiClient)
                .call(
                        any(AiPrompt.class),
                        eq(editPlanAiResponseConverter),
                        validation.capture(),
                        any(Object[].class)
                );

        EditPlanAiResponse response = new EditPlanAiResponse(
                "서울 여행",
                planWithAttractions(3, 3)
                        .planDays(),
                List.of(),
                true
        );

        assertTrue(validation
                .getValue()
                .apply(response)
                .stream()
                .anyMatch(failure -> failure.startsWith("changes:")));
    }

    @Test
    @DisplayName("재선택 전용 응답의 후보 ID를 일정 슬롯에 연결")
    void connectsSelectedCandidateIdToSchedule() {

        JsonNode schema = JsonMapper
                .builder()
                .build()
                .readTree(
                        new BeanOutputConverter<>(PlaceReselectResponse.class)
                                .getJsonSchema());

        assertEquals(
                "string",
                schema
                        .get("properties")
                        .get("selectedCandidateId")
                        .get("type")
                        .asText());

        assertTrue(schema
                .get("required")
                .toString()
                .contains("\"selectedCandidateId\""));

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates
                .record(
                        new PlaceWithRouteResult(
                                true,
                                "첨성대",
                                "경상북도 경주시 첨성로 140-25",
                                "129.219063",
                                "35.834683",
                                15,
                                "kakao:8137362",
                                "AT4",
                                "여행 > 관광,명소"));

        doReturn(new PlaceReselectResponse("kakao:8137362", null))
                .when(openAiClient)
                .call(
                        any(AiPrompt.class),
                        any(BeanOutputConverter.class),
                        any(Object[].class));

        PlanScheduleDetail result = handler()
                .reselectPlace(
                        prompt,
                        candidates);

        assertEquals("kakao:8137362", result.candidateId());
    }

    @Test
    @DisplayName("동일 이름 후보가 여러 개여도 후보 ID를 임의 복구하지 않음")
    void doesNotRecoverAmbiguousCandidate() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates
                .record(
                        place(
                                "kakao:1",
                                "동백섬"));

        candidates
                .record(
                        place(
                                "kakao:2",
                                "동백섬"));

        doReturn(new PlaceReselectResponse("kakao:missing", null))
                .when(openAiClient)
                .call(
                        any(AiPrompt.class),
                        any(BeanOutputConverter.class),
                        any(Object[].class));

        PlanScheduleDetail result = handler()
                .reselectPlace(
                        prompt,
                        candidates);

        assertNull(result.candidateId());
    }

    @Test
    @DisplayName("검색 실패 후보에는 후보 ID를 생성하거나 연결하지 않음")
    void doesNotConnectCandidateWhenSearchFailed() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates
                .record(
                        new PlaceWithRouteResult(
                                false,
                                "동백섬",
                                "부산",
                                "129.1",
                                "35.1",
                                null,
                                "kakao:1",
                                "AT4",
                                "여행 > 관광,명소"));

        doReturn(new PlaceReselectResponse("kakao:1", null))
                .when(openAiClient)
                .call(
                        any(AiPrompt.class),
                        any(BeanOutputConverter.class),
                        any(Object[].class));

        PlanScheduleDetail result = handler()
                .reselectPlace(
                        prompt,
                        candidates);

        assertNull(result.candidateId());
        assertNull(candidates.find("kakao:1"));
    }

    private TravelRecommendHandler handler() {

        return new TravelRecommendHandler(
                openAiClient,
                objectMapper,
                createPlanAiResponseConverter,
                editPlanAiResponseConverter,
                rebuildPlanDayResponseConverter,
                tourismTool);
    }

    private PlaceWithRouteResult place(
            String candidateId,
            String name
    ) {

        return new PlaceWithRouteResult(
                true,
                name,
                "부산",
                "129.1",
                "35.1",
                null,
                candidateId,
                "AT4",
                "여행 > 관광,명소");
    }

    private PlanScheduleDetail slot() {

        return new PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                "기존 장소",
                "부산",
                "129.1",
                "35.1",
                null,
                null,
                60,
                null,
                Set.of(),
                null,
                null,
                null);
    }

    private TravelPlanContext travelPlanContext(WalkType walkType) {

        CreateTravelRequest request = new CreateTravelRequest(
                "서울 여행",
                "서울",
                "종로구",
                LocalDate.of(2026, 9, 10),
                DateType.ONE_NIGHT_TWO_DAYS,
                Transportation.TRANSIT,
                "서울역",
                List.of(),
                TravelStyle.LESS_WALK,
                TravelTheme.HISTORY,
                List.of(),
                List.of()
        );

        TravelHealthContext healthContext = new TravelHealthContext(
                "여행자",
                DiseaseType.DIABETES,
                walkType,
                new TravelHealthContext.MealInfoContext(
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

        return new TravelPlanContext(
                request,
                List.of(healthContext)
        );
    }

    private CreatePlanAiResponse planWithAttractions(int... counts) {

        return new CreatePlanAiResponse(
                java.util.stream.IntStream
                        .range(0, counts.length)
                        .mapToObj(dayIndex ->
                                new CreatePlanAiResponse.PlanDayDetail(
                                        dayIndex + 1,
                                        LocalDate.of(2026, 9, 10)
                                                .plusDays(dayIndex),
                                        attractions(counts[dayIndex], dayIndex)
                                )
                        )
                        .toList()
        );
    }

    private List<PlanScheduleDetail> attractions(
            int count,
            int dayIndex
    ) {

        return java.util.stream.IntStream
                .range(0, count)
                .mapToObj(index -> new PlanScheduleDetail(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        LocalTime.of(9 + index, 0),
                        LocalTime.of(10 + index, 0),
                        "관광지 " + dayIndex + "-" + index,
                        "서울",
                        "126." + index,
                        "37." + index,
                        null,
                        null,
                        60,
                        10,
                        Set.of(),
                        null,
                        null,
                        "tour:" + dayIndex + "-" + index
                ))
                .toList();
    }
}
