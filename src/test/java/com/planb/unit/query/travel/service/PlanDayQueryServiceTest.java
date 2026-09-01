package com.planb.unit.query.travel.service;

import com.planb.query.travel.dto.response.PlanDayQueryResponse;
import com.planb.query.travel.repository.PlanDayQueryRepository;
import com.planb.query.travel.service.PlanDayQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanDayQueryServiceTest {

    @Mock
    private PlanDayQueryRepository planDayQueryRepository;

    private PlanDayQueryService planDayQueryService;

    @BeforeEach
    void setUp() {
        planDayQueryService =
                new PlanDayQueryService(
                        planDayQueryRepository
                );
    }

    @Test
    @DisplayName("Plan ID를 기준으로 PlanDay 목록 조회")
    void getPlanDaysByPlanId() {

        // given
        Long planId = 1L;

        List<PlanDayQueryResponse> expected =
                List.of(
                        new PlanDayQueryResponse(
                                1L,
                                1,
                                LocalDate.of(
                                        2026,
                                        9,
                                        1
                                )
                        ),
                        new PlanDayQueryResponse(
                                2L,
                                2,
                                LocalDate.of(
                                        2026,
                                        9,
                                        2
                                )
                        )
                );

        when(
                planDayQueryRepository
                        .findPlanDaysByPlanId(
                                planId
                        )
        ).thenReturn(
                expected
        );

        // when
        List<PlanDayQueryResponse> result =
                planDayQueryService
                        .getPlanDaysByPlanId(
                                planId
                        );

        // then
        assertThat(result)
                .isEqualTo(expected);

        verify(
                planDayQueryRepository
        ).findPlanDaysByPlanId(
                planId
        );
    }
}