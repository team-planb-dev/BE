package com.planb.unit.query.travel.service;

import com.planb.query.travel.dto.response.PlanQueryResponse;
import com.planb.query.travel.repository.PlanQueryRepository;
import com.planb.query.travel.service.PlanQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanQueryServiceTest {

    @Mock
    private PlanQueryRepository planQueryRepository;

    private PlanQueryService planQueryService;

    @BeforeEach
    void setUp() {
        planQueryService =
                new PlanQueryService(
                        planQueryRepository
                );
    }

    @Test
    @DisplayName("Travel ID를 기준으로 Plan 조회")
    void getPlanByTravelId() {

        // given
        Long travelId = 1L;

        PlanQueryResponse expected =
                new PlanQueryResponse(
                        10L,
                        "부산 AI 여행 일정"
                );

        when(
                planQueryRepository
                        .findPlanByTravelId(
                                travelId
                        )
        ).thenReturn(
                expected
        );

        // when
        PlanQueryResponse result =
                planQueryService
                        .getPlanByTravelId(
                                travelId
                        );

        // then
        assertThat(result)
                .isEqualTo(expected);

        verify(
                planQueryRepository
        ).findPlanByTravelId(
                travelId
        );
    }
}