package com.planb.slice.query.travel.repository;

import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.entity.Travel;
import com.planb.global.config.persistence.QueryDslConfig;
import com.planb.query.travel.dto.response.PlanDayQueryResponse;
import com.planb.query.travel.repository.PlanDayQueryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

@DataJpaTest
@Import({
        QueryDslConfig.class,
        PlanDayQueryRepository.class
})
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@ActiveProfiles("local")
class PlanDayQueryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PlanDayQueryRepository planDayQueryRepository;

    @Test
    @DisplayName("Plan ID를 기준으로 PlanDay 목록 조회")
    void findPlanDaysByPlanId() {

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

        entityManager.flush();
        entityManager.clear();

        // when
        List<PlanDayQueryResponse> result =
                planDayQueryRepository.findPlanDaysByPlanId(
                        plan.getId()
                );

        // then
        assertThat(result)
                .hasSize(2)
                .extracting(
                        PlanDayQueryResponse::planDayId,
                        PlanDayQueryResponse::dayNumber,
                        PlanDayQueryResponse::localdate
                )
                .containsExactly(
                        tuple(
                                day1.getId(),
                                1,
                                LocalDate.of(2026, 9, 1)
                        ),
                        tuple(
                                day2.getId(),
                                2,
                                LocalDate.of(2026, 9, 2)
                        )
                );
    }

    @Test
    @DisplayName("PlanDay 목록 Day Number 오름차순 조회")
    void findPlanDaysByPlanIdOrderByDayNumber() {

        // given
        Travel travel = createTravel(
                "부산 여행"
        );

        Plan plan = createPlan(
                travel,
                "부산 AI 여행 일정"
        );

        createPlanDay(
                plan,
                3,
                LocalDate.of(2026, 9, 3)
        );

        createPlanDay(
                plan,
                1,
                LocalDate.of(2026, 9, 1)
        );

        createPlanDay(
                plan,
                2,
                LocalDate.of(2026, 9, 2)
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<PlanDayQueryResponse> result =
                planDayQueryRepository.findPlanDaysByPlanId(
                        plan.getId()
                );

        // then
        assertThat(result)
                .extracting(
                        PlanDayQueryResponse::dayNumber
                )
                .containsExactly(
                        1,
                        2,
                        3
                );
    }

    @Test
    @DisplayName("다른 Plan의 PlanDay 조회 제외")
    void findPlanDaysByPlanIdExcludesOtherPlan() {

        // given
        Travel travel1 = createTravel(
                "부산 여행"
        );

        Travel travel2 = createTravel(
                "서울 여행"
        );

        Plan plan1 = createPlan(
                travel1,
                "부산 AI 여행 일정"
        );

        Plan plan2 = createPlan(
                travel2,
                "서울 AI 여행 일정"
        );

        PlanDay plan1Day = createPlanDay(
                plan1,
                1,
                LocalDate.of(2026, 9, 1)
        );

        createPlanDay(
                plan2,
                1,
                LocalDate.of(2026, 10, 1)
        );

        entityManager.flush();
        entityManager.clear();

        // when
        List<PlanDayQueryResponse> result =
                planDayQueryRepository.findPlanDaysByPlanId(
                        plan1.getId()
                );

        // then
        assertThat(result).hasSize(1);

        assertThat(
                result.get(0).planDayId()
        ).isEqualTo(
                plan1Day.getId()
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
}