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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;

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
}