package com.planb.unit.ai.handler;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.handler.MissingSlotFiller;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.entity.constant.CourseType;
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
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class MissingSlotFillerTest {

    @Mock
    private TourismTool tourismTool;

    private MissingSlotFiller missingSlotFiller;

    @BeforeEach
    void setUp() {

        missingSlotFiller = new MissingSlotFiller(tourismTool);

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

        CreatePlanAiResponse filled = missingSlotFiller.fill(
                response(attraction("첨성대", LocalTime.of(9, 0))),
                List.of(healthContext()),
                candidates
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

        CreatePlanAiResponse filled = missingSlotFiller.fill(
                response(attraction("첨성대", LocalTime.of(9, 0))),
                List.of(healthContext()),
                candidates
        );

        assertThat(schedules(filled))
                .extracting(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .containsExactly("첨성대", "대릉원");
    }

    @Test
    @DisplayName("빠진 식사 슬롯을 등록 식사시각에 실제 메뉴명으로 추가")
    void fillsMissingMealSlot() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(restaurantItem("9", "교리김밥", "129.21", "35.83"));

        CreatePlanAiResponse filled = missingSlotFiller.fill(
                response(
                        attraction("첨성대", LocalTime.of(9, 0)),
                        attraction("대릉원", LocalTime.of(11, 0)),
                        attraction("동궁과 월지", LocalTime.of(13, 0))
                ),
                List.of(healthContext()),
                candidates
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
    @DisplayName("채울 후보가 없으면 일정을 그대로 둠")
    void keepsPlanWhenNoCandidate() {

        CreatePlanAiResponse original = response(
                attraction("첨성대", LocalTime.of(9, 0))
        );

        CreatePlanAiResponse filled = missingSlotFiller.fill(
                original,
                List.of(healthContext()),
                new PlaceCandidateContext()
        );

        assertThat(schedules(filled))
                .hasSize(1);
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
                DiseaseType.DIABETES,
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

        return item(contentId, "12", title, mapX, mapY);
    }

    private Kor2KeywordSearchResponse.Item restaurantItem(
            String contentId,
            String title,
            String mapX,
            String mapY
    ) {

        return item(contentId, "39", title, mapX, mapY);
    }

    private Kor2KeywordSearchResponse.Item item(
            String contentId,
            String contentTypeId,
            String title,
            String mapX,
            String mapY
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
                null,
                null
        );
    }
}
