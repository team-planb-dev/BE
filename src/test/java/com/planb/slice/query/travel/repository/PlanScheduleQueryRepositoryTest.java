package com.planb.slice.query.travel.repository;

import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.travel.repository.PlanScheduleQueryRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        PlanScheduleQueryRepository.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("test")
class PlanScheduleQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlanScheduleQueryRepository planScheduleQueryRepository;

    @Test
    @DisplayName("PlanDay ID 목록을 기준으로 PlanSchedule 목록 조회")
    void findPlanSchedulesByPlanDayIds() {

        // given
        Travel travel = createTravel(
                "부산 여행"
        );

        Plan plan = createPlan(
                travel,
                "부산 AI 여행 일정"
        );

        PlanDay day1 = createPlanDay(
                plan,
                1,
                LocalDate.of(2026, 9, 1)
        );

        PlanDay day2 = createPlanDay(
                plan,
                2,
                LocalDate.of(2026, 9, 2)
        );

        PlanSchedule schedule1 = createPlanSchedule(
                day1,
                ScheduleType.LUNCH,
                CourseType.RESTAURANT,
                LocalTime.of(12, 0),
                "부산 식당",
                Set.of(
                        RecommendationTag.LOCAL_FOOD,
                        RecommendationTag.FOOD_PREFERENCE
                )
        );

        PlanSchedule schedule2 = createPlanSchedule(
                day2,
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(14, 0),
                "부산 관광지",
                Set.of(
                        RecommendationTag.HISTORY_CULTURE
                )
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<PlanSchedule> result =
                planScheduleQueryRepository
                        .findPlanSchedulesByPlanDayIds(
                                List.of(
                                        day1.getId(),
                                        day2.getId()
                                )
                        );

        // then
        assertThat(result)
                .hasSize(2)
                .extracting(
                        PlanSchedule::getId
                )
                .containsExactlyInAnyOrder(
                        schedule1.getId(),
                        schedule2.getId()
                );
    }

    @Test
    @DisplayName("PlanSchedule 태그 Fetch Join 조회")
    void findPlanSchedulesByPlanDayIdsFetchJoinTags() {

        // given
        Travel travel = createTravel(
                "부산 여행"
        );

        Plan plan = createPlan(
                travel,
                "부산 AI 여행 일정"
        );

        PlanDay planDay = createPlanDay(
                plan,
                1,
                LocalDate.of(2026, 9, 1)
        );

        createPlanSchedule(
                planDay,
                ScheduleType.LUNCH,
                CourseType.RESTAURANT,
                LocalTime.of(12, 0),
                "부산 식당",
                Set.of(
                        RecommendationTag.LOCAL_FOOD,
                        RecommendationTag.MEAL_TIME_APPLIED
                )
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<PlanSchedule> result =
                planScheduleQueryRepository
                        .findPlanSchedulesByPlanDayIds(
                                List.of(
                                        planDay.getId()
                                )
                        );

        // then
        PlanSchedule schedule =
                result.get(0);

        assertThat(
                Hibernate.isInitialized(
                        schedule.getTags()
                )
        ).isTrue();

        assertThat(
                schedule.getTags()
        ).containsExactlyInAnyOrder(
                RecommendationTag.LOCAL_FOOD,
                RecommendationTag.MEAL_TIME_APPLIED
        );
    }

    @Test
    @DisplayName("PlanSchedule 시작 시간 오름차순 조회")
    void findPlanSchedulesByPlanDayIdsOrderByStartTime() {

        // given
        Travel travel = createTravel(
                "부산 여행"
        );

        Plan plan = createPlan(
                travel,
                "부산 AI 여행 일정"
        );

        PlanDay planDay = createPlanDay(
                plan,
                1,
                LocalDate.of(2026, 9, 1)
        );

        createPlanSchedule(
                planDay,
                ScheduleType.DINNER,
                CourseType.RESTAURANT,
                LocalTime.of(18, 0),
                "저녁 식당",
                Set.of(
                        RecommendationTag.LOCAL_FOOD
                )
        );

        createPlanSchedule(
                planDay,
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(10, 0),
                "오전 관광지",
                Set.of(
                        RecommendationTag.HISTORY_CULTURE
                )
        );

        createPlanSchedule(
                planDay,
                ScheduleType.LUNCH,
                CourseType.RESTAURANT,
                LocalTime.of(12, 0),
                "점심 식당",
                Set.of(
                        RecommendationTag.FOOD_PREFERENCE
                )
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<PlanSchedule> result =
                planScheduleQueryRepository
                        .findPlanSchedulesByPlanDayIds(
                                List.of(
                                        planDay.getId()
                                )
                        );

        // then
        assertThat(result)
                .extracting(
                        PlanSchedule::getStartTime
                )
                .containsExactly(
                        LocalTime.of(10, 0),
                        LocalTime.of(12, 0),
                        LocalTime.of(18, 0)
                );
    }

    @Test
    @DisplayName("조회 대상에 없는 PlanDay의 PlanSchedule 제외")
    void findPlanSchedulesByPlanDayIdsExcludesOtherPlanDay() {

        // given
        Travel travel = createTravel(
                "부산 여행"
        );

        Plan plan = createPlan(
                travel,
                "부산 AI 여행 일정"
        );

        PlanDay targetDay = createPlanDay(
                plan,
                1,
                LocalDate.of(2026, 9, 1)
        );

        PlanDay otherDay = createPlanDay(
                plan,
                2,
                LocalDate.of(2026, 9, 2)
        );

        PlanSchedule targetSchedule = createPlanSchedule(
                targetDay,
                ScheduleType.LUNCH,
                CourseType.RESTAURANT,
                LocalTime.of(12, 0),
                "조회 대상 식당",
                Set.of(
                        RecommendationTag.LOCAL_FOOD
                )
        );

        createPlanSchedule(
                otherDay,
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(14, 0),
                "조회 제외 관광지",
                Set.of(
                        RecommendationTag.NATURAL_SCENERY
                )
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<PlanSchedule> result =
                planScheduleQueryRepository
                        .findPlanSchedulesByPlanDayIds(
                                List.of(
                                        targetDay.getId()
                                )
                        );

        // then
        assertThat(result).hasSize(1);

        assertThat(
                result.get(0).getId()
        ).isEqualTo(
                targetSchedule.getId()
        );
    }

    private Travel createTravel(String travelName) {

        Travel travel = Travel.builder()
                .travelName(travelName)
                .build();

        entityManager.persist(travel);

        return travel;
    }

    private Plan createPlan(
            Travel travel,
            String planName
    ) {

        Plan plan = Plan.builder()
                .travel(travel)
                .planName(planName)
                .build();

        entityManager.persist(plan);

        return plan;
    }

    private PlanDay createPlanDay(
            Plan plan,
            Integer dayNumber,
            LocalDate planDate
    ) {

        PlanDay planDay = PlanDay.builder()
                .plan(plan)
                .dayNumber(dayNumber)
                .planDate(planDate)
                .build();

        entityManager.persist(planDay);

        return planDay;
    }

    private PlanSchedule createPlanSchedule(
            PlanDay planDay,
            ScheduleType scheduleType,
            CourseType courseType,
            LocalTime startTime,
            String locationName,
            Set<RecommendationTag> tags
    ) {

        PlanSchedule planSchedule =
                PlanSchedule.builder()
                        .planDay(planDay)
                        .scheduleType(scheduleType)
                        .courseType(courseType)
                        .startTime(startTime)
                        .endTime(
                                startTime.plusHours(1)
                        )
                        .locationName(locationName)
                        .location("부산광역시")
                        .stayMinutes(60)
                        .travelMinutes(20)
                        .tags(tags)
                        .build();

        entityManager.persist(planSchedule);

        return planSchedule;
    }
}