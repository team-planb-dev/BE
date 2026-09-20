package com.planb.unit.ai.handler;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.handler.MissingSlotCompleter;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MissingSlotCompleterTest {

    @Mock
    private TourismTool tourismTool;

    private MissingSlotCompleter missingSlotCompleter;

    @BeforeEach
    void setUp() {

        missingSlotCompleter = new MissingSlotCompleter(tourismTool);

        lenient()
                .when(tourismTool.getRestaurantDetail(anyString()))
                .thenReturn(intro("대표메뉴"));
    }

    @Test
    @DisplayName("부족한 관광 슬롯을 후보로 채움")
    void fillsMissingTouristPlaces() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(attractionItem("1", "첨성대", "129.22", "35.83"));
        candidates.record(attractionItem("2", "대릉원", "129.21", "35.83"));
        candidates.record(attractionItem("3", "동궁과 월지", "129.22", "35.83"));

        CreatePlanAiResponse filled = missingSlotCompleter.complete(
                response(attraction("첨성대", LocalTime.of(9, 0))),
                List.of(healthContext()),
                candidates,
                Set.of(),
                Set.of()
        );

        List<CreatePlanAiResponse.PlanScheduleDetail> schedules = schedules(filled);

        assertThat(schedules)
                .filteredOn(slot -> slot.courseType() == CourseType.ATTRACTION)
                .hasSize(3);

        assertThat(schedules)
                .extracting(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .contains("대릉원", "동궁과 월지");
    }

    @Test
    @DisplayName("이미 쓴 장소는 다시 채우지 않음")
    void skipsAlreadyUsedPlaces() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(attractionItem("1", "첨성대", "129.22", "35.83"));
        candidates.record(attractionItem("2", "대릉원", "129.21", "35.83"));

        CreatePlanAiResponse filled = missingSlotCompleter.complete(
                response(attraction("첨성대", LocalTime.of(9, 0))),
                List.of(healthContext()),
                candidates,
                Set.of(),
                Set.of()
        );

        assertThat(schedules(filled))
                .extracting(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .containsExactly("첨성대", "대릉원");
    }

    @Test
    @DisplayName("응답 밖에서 이미 쓴 장소는 채우지 않음")
    void skipsPlacesUsedOutsideResponse() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(attractionItem("1", "첨성대", "129.22", "35.83"));
        candidates.record(attractionItem("2", "대릉원", "129.21", "35.83"));
        candidates.record(attractionItem("3", "동궁과 월지", "129.22", "35.83"));

        CreatePlanAiResponse filled = missingSlotCompleter.complete(
                response(attraction("첨성대", LocalTime.of(9, 0))),
                List.of(healthContext()),
                candidates,
                Set.of("대릉원"),
                Set.of()
        );

        assertThat(schedules(filled))
                .extracting(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .doesNotContain("대릉원")
                .contains("동궁과 월지");
    }

    @Test
    @DisplayName("호출부가 넘긴 사용 장소 집합을 변형하지 않음")
    void keepsCallerUsedNamesUntouched() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(attractionItem("1", "첨성대", "129.22", "35.83"));
        candidates.record(attractionItem("2", "대릉원", "129.21", "35.83"));
        candidates.record(attractionItem("3", "동궁과 월지", "129.22", "35.83"));

        Set<String> usedNames = new HashSet<>(Set.of("불국사"));

        missingSlotCompleter.complete(
                response(attraction("첨성대", LocalTime.of(9, 0))),
                List.of(healthContext()),
                candidates,
                usedNames,
                Set.of()
        );

        assertThat(usedNames)
                .containsExactly("불국사");
    }

    @Test
    @DisplayName("빠진 식사 슬롯을 등록 식사시각에 실제 메뉴명으로 추가")
    void fillsMissingMealSlot() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(restaurantItem("9", "교리김밥", "129.21", "35.83"));

        CreatePlanAiResponse filled = missingSlotCompleter.complete(
                response(
                        attraction("첨성대", LocalTime.of(9, 0)),
                        attraction("대릉원", LocalTime.of(11, 0)),
                        attraction("동궁과 월지", LocalTime.of(13, 0))
                ),
                List.of(healthContext()),
                candidates,
                Set.of(),
                Set.of()
        );

        assertThat(schedules(filled))
                .filteredOn(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .singleElement()
                .satisfies(slot -> {
                    assertThat(slot.locationName())
                            .isEqualTo("교리김밥");

                    assertThat(slot.startTime())
                            .isEqualTo(LocalTime.of(12, 0));

                    assertThat(slot.restaurantDetail().menuName())
                            .isEqualTo("대표메뉴");
                });
    }

    @Test
    @DisplayName("식사 슬롯 삽입으로 변경된 인접 경로만 이동시간 무효화")
    void invalidatesOnlyTravelMinutesWhosePredecessorChanged() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(restaurantItem("9", "교리김밥", "129.21", "35.83"));

        CreatePlanAiResponse filled = missingSlotCompleter.complete(
                response(
                        attraction("첨성대", LocalTime.of(9, 0)),
                        attraction("대릉원", LocalTime.of(13, 0)),
                        attraction("동궁과 월지", LocalTime.of(15, 0))
                ),
                List.of(healthContext()),
                candidates,
                Set.of(),
                Set.of()
        );

        assertThat(schedules(filled))
                .extracting(
                        CreatePlanAiResponse.PlanScheduleDetail::locationName,
                        CreatePlanAiResponse.PlanScheduleDetail::travelMinutes
                )
                .containsExactly(
                        tuple("첨성대", 10),
                        tuple("교리김밥", null),
                        tuple("대릉원", null),
                        tuple("동궁과 월지", 10)
                );
    }

    @Test
    @DisplayName("전날 마지막 식사 삽입으로 변경된 다음 날 첫 이동시간 무효화")
    void invalidatesNextDayFirstTravelMinutesWhenPreviousDayEndsWithInsertedMeal() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(restaurantItem("9", "교리김밥", "129.21", "35.83"));

        TravelHealthContext health = new TravelHealthContext(
                "동행인",
                List.of(DiseaseType.DIABETES),
                WalkType.MINIMAL,
                new TravelHealthContext.MealInfoContext(
                        true,
                        false,
                        null,
                        false,
                        null,
                        true,
                        LocalTime.of(18, 0)
                ),
                List.of(),
                List.of()
        );

        CreatePlanAiResponse filled = missingSlotCompleter.complete(
                twoDays(
                        List.of(
                                attraction("첨성대", LocalTime.of(9, 0)),
                                attraction("대릉원", LocalTime.of(11, 0))
                        ),
                        List.of(
                                attraction("불국사", LocalTime.of(9, 0)),
                                attraction("석굴암", LocalTime.of(11, 0))
                        )
                ),
                List.of(health),
                candidates,
                Set.of(),
                Set.of()
        );

        assertThat(daySchedules(filled, 2))
                .extracting(
                        CreatePlanAiResponse.PlanScheduleDetail::locationName,
                        CreatePlanAiResponse.PlanScheduleDetail::travelMinutes
                )
                .containsExactly(
                        tuple("불국사", null),
                        tuple("석굴암", 10)
                );
    }

    @Test
    @DisplayName("채워 넣은 관광 슬롯의 분류 기반 태그")
    void tagsFilledTouristPlaceByCategory() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(attractionItem("1", "첨성대", "129.22", "35.83"));
        candidates.record(attractionItem("2", "대릉원", "129.21", "35.83"));

        CreatePlanAiResponse filled = missingSlotCompleter.complete(
                response(attraction("첨성대", LocalTime.of(9, 0))),
                List.of(healthContext()),
                candidates,
                Set.of(),
                Set.of()
        );

        assertThat(schedules(filled))
                .filteredOn(slot -> "대릉원".equals(slot.locationName()))
                .singleElement()
                .satisfies(slot ->
                        assertThat(slot.tags())
                                .containsExactly(RecommendationTag.HISTORY_CULTURE));
    }

    @Test
    @DisplayName("채울 후보가 없으면 일정을 그대로 둠")
    void keepsPlanWhenNoCandidate() {

        CreatePlanAiResponse original = response(
                attraction("첨성대", LocalTime.of(9, 0))
        );

        CreatePlanAiResponse filled = missingSlotCompleter.complete(
                original,
                List.of(healthContext()),
                new PlaceCandidateContext(),
                Set.of(),
                Set.of()
        );

        assertThat(schedules(filled))
                .hasSize(1);
    }

    @Test
    @DisplayName("일정에 이미 쓴 메뉴는 다른 식당으로도 다시 채우지 않음")
    void skipsCandidateWhoseMenuIsAlreadyUsed() {

        lenient()
                .when(tourismTool.getRestaurantDetail("101"))
                .thenReturn(intro("삼계탕"));

        lenient()
                .when(tourismTool.getRestaurantDetail("102"))
                .thenReturn(intro("칼국수"));

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(restaurantItem("101", "백제삼계탕", "129.21", "35.83"));

        candidates.record(restaurantItem("102", "하니칼국수", "129.23", "35.84"));

        CreatePlanAiResponse filled = missingSlotCompleter.complete(
                twoDays(
                        List.of(
                                attraction("첨성대", LocalTime.of(9, 0)),
                                attraction("대릉원", LocalTime.of(11, 0)),
                                attraction("동궁과 월지", LocalTime.of(13, 0)),
                                restaurant("고려삼계탕", "삼계탕", LocalTime.of(12, 0))
                        ),
                        List.of(
                                attraction("불국사", LocalTime.of(9, 0)),
                                attraction("석굴암", LocalTime.of(11, 0)),
                                attraction("감은사지", LocalTime.of(13, 0))
                        )
                ),
                List.of(healthContext()),
                candidates,
                Set.of(),
                Set.of()
        );

        assertThat(daySchedules(filled, 2))
                .filteredOn(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .singleElement()
                .satisfies(slot ->
                        assertThat(slot.restaurantDetail()
                                .menuName())
                                .isEqualTo("칼국수"));
    }

    @Test
    @DisplayName("후보의 대표메뉴가 전부 이미 쓰였으면 식사 슬롯을 채우지 않음")
    void leavesMealEmptyWhenEveryCandidateMenuIsUsed() {

        lenient()
                .when(tourismTool.getRestaurantDetail("101"))
                .thenReturn(intro("삼계탕"));

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(restaurantItem("101", "백제삼계탕", "129.21", "35.83"));

        CreatePlanAiResponse filled = missingSlotCompleter.complete(
                twoDays(
                        List.of(
                                attraction("첨성대", LocalTime.of(9, 0)),
                                attraction("대릉원", LocalTime.of(11, 0)),
                                attraction("동궁과 월지", LocalTime.of(13, 0)),
                                restaurant("고려삼계탕", "삼계탕", LocalTime.of(12, 0))
                        ),
                        List.of(
                                attraction("불국사", LocalTime.of(9, 0)),
                                attraction("석굴암", LocalTime.of(11, 0)),
                                attraction("감은사지", LocalTime.of(13, 0))
                        )
                ),
                List.of(healthContext()),
                candidates,
                Set.of(),
                Set.of()
        );

        assertThat(daySchedules(filled, 2))
                .filteredOn(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .isEmpty();
    }

    private List<CreatePlanAiResponse.PlanScheduleDetail> daySchedules(
            CreatePlanAiResponse response,
            int dayNumber
    ) {

        return response
                .planDays()
                .stream()
                .filter(day -> day.dayNumber() == dayNumber)
                .findFirst()
                .orElseThrow()
                .schedules();
    }

    private CreatePlanAiResponse twoDays(
            List<CreatePlanAiResponse.PlanScheduleDetail> first,
            List<CreatePlanAiResponse.PlanScheduleDetail> second
    ) {

        return new CreatePlanAiResponse(
                List.of(
                        new CreatePlanAiResponse.PlanDayDetail(
                                1,
                                LocalDate.of(2026, 9, 19),
                                first
                        ),
                        new CreatePlanAiResponse.PlanDayDetail(
                                2,
                                LocalDate.of(2026, 9, 20),
                                second
                        )
                )
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail restaurant(
            String name,
            String menuName,
            LocalTime startTime
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.LUNCH,
                CourseType.RESTAURANT,
                startTime,
                startTime.plusMinutes(60),
                name,
                "경상북도 경주시",
                "129.22",
                "35.83",
                null,
                null,
                60,
                10,
                Set.of(),
                null,
                new CreatePlanAiResponse.RestaurantDetail(
                        menuName,
                        null,
                        null,
                        null,
                        null,
                        "경상북도 경주시",
                        "129.22",
                        "35.83",
                        null
                )
        );
    }

    private List<CreatePlanAiResponse.PlanScheduleDetail> schedules(CreatePlanAiResponse response) {

        return response
                .planDays()
                .getFirst()
                .schedules();
    }

    private CreatePlanAiResponse response(CreatePlanAiResponse.PlanScheduleDetail... schedules) {

        return new CreatePlanAiResponse(
                List.of(
                        new CreatePlanAiResponse.PlanDayDetail(
                                1,
                                LocalDate.of(2026, 9, 19),
                                List.of(schedules)
                        )
                )
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail attraction(
            String name,
            LocalTime startTime
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                startTime,
                startTime.plusMinutes(60),
                name,
                "경상북도 경주시",
                "129.22",
                "35.83",
                null,
                null,
                60,
                10,
                Set.of(),
                null,
                null
        );
    }

    private TravelHealthContext healthContext() {

        return new TravelHealthContext(
                "동행인",
                List.of(DiseaseType.DIABETES),
                WalkType.MODERATE,
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
        );
    }

    private Kor2RestaurantIntroResponse intro(String firstMenu) {

        return new Kor2RestaurantIntroResponse(
                new Kor2RestaurantIntroResponse.Response(
                        new Kor2RestaurantIntroResponse.Header("0000", "OK"),
                        new Kor2RestaurantIntroResponse.Body(
                                new Kor2RestaurantIntroResponse.Items(
                                        List.of(
                                                new Kor2RestaurantIntroResponse.Item(
                                                        "9",
                                                        "39",
                                                        firstMenu,
                                                        firstMenu
                                                )
                                        )
                                ),
                                1,
                                1,
                                1
                        )
                )
        );
    }

    private Kor2KeywordSearchResponse.Item attractionItem(
            String contentId,
            String title,
            String mapX,
            String mapY
    ) {

        return item(contentId, "12", title, mapX, mapY, "HS01");
    }

    private Kor2KeywordSearchResponse.Item restaurantItem(
            String contentId,
            String title,
            String mapX,
            String mapY
    ) {

        return item(contentId, "39", title, mapX, mapY, "FD01");
    }

    private Kor2KeywordSearchResponse.Item item(
            String contentId,
            String contentTypeId,
            String title,
            String mapX,
            String mapY,
            String categoryCode
    ) {

        return new Kor2KeywordSearchResponse.Item(
                "경상북도 경주시",
                "",
                null,
                contentId,
                contentTypeId,
                null,
                null,
                null,
                null,
                mapX,
                mapY,
                null,
                null,
                null,
                title,
                null,
                null,
                null,
                categoryCode,
                null
        );
    }
}
