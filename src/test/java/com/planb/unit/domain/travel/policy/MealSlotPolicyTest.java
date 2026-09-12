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
    @DisplayName("등록 식사시각을 지나는 하루에 식사 슬롯이 없으면 누락으로 판단")
    void spanningConfiguredMealWithoutSlotIsMissing() {

        CreatePlanAiResponse.PlanDayDetail day = day(
                attraction(
                        LocalTime.of(9, 40),
                        LocalTime.of(11, 40)
                ),
                attraction(
                        LocalTime.of(12, 30),
                        LocalTime.of(14, 10)
                )
        );

        assertThat(
                MealSlotPolicy.missingMeals(
                        day,
                        List.of(healthContext())
                )
        ).containsExactly(ScheduleType.LUNCH);
    }

    @Test
    @DisplayName("식사 슬롯이 있는 하루는 누락 없음")
    void spanningConfiguredMealWithSlotIsSatisfied() {

        CreatePlanAiResponse.PlanDayDetail day = day(
                attraction(
                        LocalTime.of(9, 40),
                        LocalTime.of(11, 40)
                ),
                meal(
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
        ).isEmpty();
    }

    @Test
    @DisplayName("등록 식사시각을 지나지 않는 하루는 식사 슬롯을 요구하지 않음")
    void dayEndingBeforeConfiguredMealRequiresNothing() {

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
        ).isEmpty();
    }

    @Test
    @DisplayName("식사시간 미적용 동행인만 있으면 식사 슬롯을 요구하지 않음")
    void mealInfoNotAppliedRequiresNothing() {

        TravelHealthContext notApplied = new TravelHealthContext(
                "동행인",
                DiseaseType.DIABETES,
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
        ).isEmpty();
    }

    @Test
    @DisplayName("아침과 점심을 모두 지나는 하루는 두 식사를 모두 요구")
    void spanningTwoConfiguredMealsRequiresBoth() {

        CreatePlanAiResponse.PlanDayDetail day = day(
                attraction(
                        LocalTime.of(7, 30),
                        LocalTime.of(11, 40)
                ),
                attraction(
                        LocalTime.of(12, 30),
                        LocalTime.of(14, 10)
                )
        );

        assertThat(
                MealSlotPolicy.missingMeals(
                        day,
                        List.of(healthContext())
                )
        ).containsExactly(
                ScheduleType.BREAKFAST,
                ScheduleType.LUNCH
        );
    }

    private CreatePlanAiResponse.PlanDayDetail day(
            CreatePlanAiResponse.PlanScheduleDetail... schedules
    ) {

        return new CreatePlanAiResponse.PlanDayDetail(
                1,
                LocalDate.of(2026, 9, 12),
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
            LocalTime startTime,
            LocalTime endTime
    ) {

        return schedule(
                ScheduleType.LUNCH,
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
                DiseaseType.DIABETES,
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
