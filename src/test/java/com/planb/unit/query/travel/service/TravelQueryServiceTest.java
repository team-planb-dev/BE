package com.planb.unit.query.travel.service;

import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.query.travel.dto.response.TravelConditionQueryResponse;
import com.planb.query.travel.repository.TravelQueryRepository;
import com.planb.query.travel.service.TravelQueryService;
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
class TravelQueryServiceTest {

    @Mock
    private TravelQueryRepository travelQueryRepository;

    private TravelQueryService travelQueryService;

    @BeforeEach
    void setUp() {
        travelQueryService =
                new TravelQueryService(
                        travelQueryRepository
                );
    }

    @Test
    @DisplayName("Travel ID를 기준으로 여행 조건 조회")
    void getTravelConditionQueryResponse() {

        // given
        Long travelId = 1L;

        TravelConditionQueryResponse expected =
                new TravelConditionQueryResponse(
                        TravelStyle.LESS_WALK,
                        TravelTheme.TASTE
                );

        when(
                travelQueryRepository
                        .findTravelConditionById(
                                travelId
                        )
        ).thenReturn(
                expected
        );

        // when
        TravelConditionQueryResponse result =
                travelQueryService
                        .getTravelConditionQueryResponse(
                                travelId
                        );

        // then
        assertThat(result)
                .isEqualTo(expected);

        verify(travelQueryRepository)
                .findTravelConditionById(
                        travelId
                );
    }
}