package com.planb.unit.domain.travel.policy;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.policy.MealSlotPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MealSlotPolicyTest {

    @Test
    @DisplayName("이미 있는 식사는 채울 대상에서 빠짐")
    void existingMealIsNotAFillTarget() {

        CreatePlanAiResponse.PlanDayDetail day = day(
                attraction(
                        LocalTime.of(9, 40),
                        LocalTime.of(11, 40)
                ),
                meal(
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        LocalTime.of(13, 0)
                ),
                attraction(
                        LocalTime.of(13, 30),
                        LocalTime.of(15, 0)
                )
        );

        assertThat(
                MealSlotPolicy.missingMeals(
                        day,
                        List.of(healthContext())
                )
        )
                .containsExactly(
                ScheduleType.BREAKFAST,
                ScheduleType.DINNER
        );
    }

    @Test
    @DisplayName("등록 식사시각을 지나지 않는 하루도 등록된 식사를 모두 채울 대상으로 판단")
    void dayEndingBeforeConfiguredMealStillNeedsFilling() {

        CreatePlanAiResponse.PlanDayDetail day = day(
                attraction(
                        LocalTime.of(9, 0),
                        LocalTime.of(11, 0)
                )
        );

        assertThat(
                MealSlotPolicy.missingMeals(
                        day,
                        List.of(healthContext())
                )
        )
                .containsExactly(
                ScheduleType.BREAKFAST,
                ScheduleType.LUNCH,
                ScheduleType.DINNER
        );
    }

    @Test
    @DisplayName("식사시간 미적용 동행인만 있으면 식사 슬롯을 요구하지 않음")
    void mealInfoNotAppliedRequiresNothing() {

        TravelHealthContext notApplied = new TravelHealthContext(
                "동행인",
                List.of(DiseaseType.DIABETES),
                WalkType.MODERATE,
                new TravelHealthContext.MealInfoContext(
                        false,
                        true,
                        LocalTime.of(8, 0),
                        true,
                        LocalTime.of(12, 0),
                        true,
                        LocalTime.of(18, 0)
                ),
                List.of(),
                List.of()
        );

        CreatePlanAiResponse.PlanDayDetail day = day(
                attraction(
                        LocalTime.of(9, 40),
                        LocalTime.of(14, 10)
                )
        );

        assertThat(
                MealSlotPolicy.missingMeals(
                        day,
                        List.of(notApplied)
                )
        )
                .isEmpty();
    }

    @Test
    @DisplayName("등록된 식사가 모두 있는 하루는 채울 대상 없음")
    void allRegisteredMealsPresentNeedsNothing() {

        CreatePlanAiResponse.PlanDayDetail day = day(
                meal(
                        ScheduleType.BREAKFAST,
                        LocalTime.of(8, 0),
                        LocalTime.of(9, 0)
                ),
                meal(
                        ScheduleType.LUNCH,
                        LocalTime.of(12, 0),
                        LocalTime.of(13, 0)
                ),
                meal(
                        ScheduleType.DINNER,
                        LocalTime.of(18, 0),
                        LocalTime.of(19, 0)
                )
        );

        assertThat(
                MealSlotPolicy.missingMeals(
                        day,
                        List.of(healthContext())
                )
        )
                .isEmpty();
    }

    @Test
    @DisplayName("첫날 아침은 없어도 거부하지 않음")
    void firstDayBreakfastIsExemptFromRejection() {

        CreatePlanAiResponse.PlanDayDetail day = day(
                1,
                attraction(
                        LocalTime.of(9, 0),
                        LocalTime.of(11, 0)
                )
        );

        assertThat(
                MealSlotPolicy.requiredMissingMeals(
                        day,
                        List.of(healthContext()),
                        2
                )
        )
                .containsExactly(
                ScheduleType.LUNCH,
                ScheduleType.DINNER
        );
    }

    @Test
    @DisplayName("마지막 날 저녁은 없어도 거부하지 않음")
    void lastDayDinnerIsExemptFromRejection() {

        CreatePlanAiResponse.PlanDayDetail day = day(
                2,
                attraction(
                        LocalTime.of(9, 0),
                        LocalTime.of(16, 18)
                )
        );

        assertThat(
                MealSlotPolicy.requiredMissingMeals(
                        day,
                        List.of(healthContext()),
                        2
                )
        )
                .containsExactly(
                ScheduleType.BREAKFAST,
                ScheduleType.LUNCH
        );
    }

    @Test
    @DisplayName("중간 날은 등록된 세 끼를 모두 요구")
    void middleDayRequiresEveryRegisteredMeal() {

        CreatePlanAiResponse.PlanDayDetail day = day(
                2,
                attraction(
                        LocalTime.of(9, 0),
                        LocalTime.of(16, 18)
                )
        );

        assertThat(
                MealSlotPolicy.requiredMissingMeals(
                        day,
                        List.of(healthContext()),
                        3
                )
        )
                .containsExactly(
                ScheduleType.BREAKFAST,
                ScheduleType.LUNCH,
                ScheduleType.DINNER
        );
    }

    private CreatePlanAiResponse.PlanDayDetail day(
            CreatePlanAiResponse.PlanScheduleDetail... schedules
    ) {

        return day(1, schedules);
    }

    private CreatePlanAiResponse.PlanDayDetail day(
            int dayNumber,
            CreatePlanAiResponse.PlanScheduleDetail... schedules
    ) {

        return new CreatePlanAiResponse.PlanDayDetail(
                dayNumber,
                LocalDate.of(2026, 9, 12).plusDays(dayNumber - 1L),
                List.of(schedules)
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail attraction(
            LocalTime startTime,
            LocalTime endTime
    ) {

        return schedule(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                startTime,
                endTime
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail meal(
            ScheduleType mealType,
            LocalTime startTime,
            LocalTime endTime
    ) {

        return schedule(
                mealType,
                CourseType.RESTAURANT,
                startTime,
                endTime
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail schedule(
            ScheduleType scheduleType,
            CourseType courseType,
            LocalTime startTime,
            LocalTime endTime
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                scheduleType,
                courseType,
                startTime,
                endTime,
                "장소",
                "주소",
                "127.0",
                "37.0",
                null,
                null,
                60,
                10,
                null,
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
                        LocalTime.of(8, 0),
                        LocalTime.of(12, 0),
                        LocalTime.of(18, 0)
                ),
                List.of(),
                List.of()
        );
    }
}
