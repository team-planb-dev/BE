package com.planb.unit.domain.travel.service;

import com.planb.domain.travel.dto.request.CreatePlanDayRequest;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.repository.PlanDayRepository;
import com.planb.domain.travel.service.PlanDayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanDayServiceTest {

    @Mock
    private PlanDayRepository planDayRepository;

    @InjectMocks
    private PlanDayService planDayService;

    @Test
    @DisplayName("PlanDay 객체 생성")
    void createPlanDay() {

        Plan plan = Plan.builder()
                .planName("부산 여행")
                .build();

        LocalDate planDate =
                LocalDate.of(
                        2026,
                        8,
                        26
                );

        CreatePlanDayRequest request =
                new CreatePlanDayRequest(
                        plan,
                        1,
                        planDate
                );

        PlanDay planDay =
                planDayService.createPlanDay(
                        request
                );

        assertSame(
                plan,
                planDay.getPlan()
        );

        assertEquals(
                1,
                planDay.getDayNumber()
        );

        assertEquals(
                planDate,
                planDay.getPlanDate()
        );
    }

    @Test
    @DisplayName("PlanDay 객체 저장")
    void savePlanDay() {

        PlanDay planDay =
                PlanDay.builder()
                        .dayNumber(1)
                        .planDate(
                                LocalDate.of(
                                        2026,
                                        8,
                                        26
                                )
                        )
                        .build();

        planDayService.savePlanDay(
                planDay
        );

        verify(planDayRepository)
                .save(planDay);
    }

    @Test
    @DisplayName("특정 Plan에 속한 PlanDay 리스트 조회")
    void findAllByPlan() {

        Plan plan =
                Plan.builder()
                        .planName("부산 여행")
                        .build();

        List<PlanDay> planDays =
                List.of(
                        PlanDay.builder()
                                .plan(plan)
                                .dayNumber(1)
                                .build()
                );

        when(
                planDayRepository
                        .findAllByPlan(plan)
        ).thenReturn(
                planDays
        );

        List<PlanDay> result =
                planDayService.findAllByPlan(
                        plan
                );

        assertEquals(
                planDays,
                result
        );
    }

    @Test
    @DisplayName("특정 Plan에 속한 PlanDay 리스트 일괄 삭제")
    void deleteAllByPlan() {

        Plan plan =
                Plan.builder()
                        .planName("부산 여행")
                        .build();

        planDayService.deleteAllByPlan(
                plan
        );

        verify(planDayRepository)
                .deleteAllByPlan(plan);
    }
}