package com.planb.unit.query.travel.service;

import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.query.travel.repository.PlanScheduleQueryRepository;
import com.planb.query.travel.service.PlanScheduleQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanScheduleQueryServiceTest {

    @Mock
    private PlanScheduleQueryRepository planScheduleQueryRepository;

    private PlanScheduleQueryService planScheduleQueryService;

    @BeforeEach
    void setUp() {
        planScheduleQueryService =
                new PlanScheduleQueryService(
                        planScheduleQueryRepository
                );
    }

    @Test
    @DisplayName("PlanDay ID 목록을 기준으로 PlanSchedule 목록 조회")
    void getPlanSchedulesByPlanDayIds() {

        // given
        List<Long> planDayIds =
                List.of(
                        1L,
                        2L
                );

        List<PlanSchedule> expected =
                List.of(
                        mock(PlanSchedule.class),
                        mock(PlanSchedule.class)
                );

        when(
                planScheduleQueryRepository
                        .findPlanSchedulesByPlanDayIds(
                                planDayIds
                        )
        ).thenReturn(
                expected
        );

        // when
        List<PlanSchedule> result =
                planScheduleQueryService
                        .getPlanSchedulesByPlanDayIds(
                                planDayIds
                        );

        // then
        assertThat(result)
                .isEqualTo(expected);

        verify(
                planScheduleQueryRepository
        ).findPlanSchedulesByPlanDayIds(
                planDayIds
        );
    }
}