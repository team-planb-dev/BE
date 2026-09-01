package com.planb.unit.query.travel.service;

import com.planb.query.travel.dto.response.RestaurantDetailQueryResponse;
import com.planb.query.travel.repository.RestaurantDetailQueryRepository;
import com.planb.query.travel.service.RestaurantDetailQueryService;
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
class RestaurantDetailQueryServiceTest {

    @Mock
    private RestaurantDetailQueryRepository restaurantDetailQueryRepository;

    private RestaurantDetailQueryService restaurantDetailQueryService;

    @BeforeEach
    void setUp() {
        restaurantDetailQueryService =
                new RestaurantDetailQueryService(
                        restaurantDetailQueryRepository
                );
    }

    @Test
    @DisplayName("PlanSchedule ID 목록을 기준으로 RestaurantDetail 목록 조회")
    void getRestaurantDetailsByPlanScheduleIds() {

        // given
        List<Long> planScheduleIds =
                List.of(
                        1L,
                        2L
                );

        List<RestaurantDetailQueryResponse> expected =
                List.of(
                        mock(RestaurantDetailQueryResponse.class),
                        mock(RestaurantDetailQueryResponse.class)
                );

        when(
                restaurantDetailQueryRepository
                        .findRestaurantDetailsByPlanScheduleIds(
                                planScheduleIds
                        )
        ).thenReturn(
                expected
        );

        // when
        List<RestaurantDetailQueryResponse> result =
                restaurantDetailQueryService
                        .getRestaurantDetailsByPlanScheduleIds(
                                planScheduleIds
                        );

        // then
        assertThat(result)
                .isEqualTo(expected);

        verify(
                restaurantDetailQueryRepository
        ).findRestaurantDetailsByPlanScheduleIds(
                planScheduleIds
        );
    }
}