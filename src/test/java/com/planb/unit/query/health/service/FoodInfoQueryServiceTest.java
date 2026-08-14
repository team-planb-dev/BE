package com.planb.unit.query.health.service;

import com.planb.query.health.repository.FoodInfoQueryRepository;
import com.planb.query.health.service.FoodInfoQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FoodInfoQueryServiceTest {

    @Mock
    private FoodInfoQueryRepository foodInfoQueryRepository;

    private FoodInfoQueryService foodInfoQueryService;

    @BeforeEach
    void setUp() {
        foodInfoQueryService =
                new FoodInfoQueryService(foodInfoQueryRepository);
    }

    @Test
    @DisplayName("Health ID를 기준으로 모든 음식 정보 삭제")
    void deleteAllByHealthIdSuccess() {

        // given
        Long healthId = 1L;

        // when
        foodInfoQueryService.deleteAllByHealthId(healthId);

        // then
        verify(foodInfoQueryRepository)
                .deleteAllByHealthId(healthId);
    }
}