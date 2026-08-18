package com.planb.unit.query.health.service;

import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.query.health.repository.HealthQueryRepository;
import com.planb.query.health.service.HealthQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthQueryServiceTest {

    @Mock
    private HealthQueryRepository healthQueryRepository;

    private HealthQueryService healthQueryService;

    @BeforeEach
    void setUp() {
        healthQueryService =
                new HealthQueryService(healthQueryRepository);
    }

    @Test
    @DisplayName("User ID를 기준으로 동행인 건강 요약 정보 조회")
    void getHealthSummaryListSuccess() {

        // given
        Long userId = 1L;

        List<HealthSummaryQueryResponse> expected =
                List.of(
                        new HealthSummaryQueryResponse(
                                1L,
                                "동행인1",
                                true,
                                DiseaseType.DIABETES,
                                true
                        ),
                        new HealthSummaryQueryResponse(
                                2L,
                                "동행인2",
                                false,
                                null,
                                false
                        )
                );

        when(healthQueryRepository
                .findHealthSummaryList(userId))
                .thenReturn(expected);

        // when
        List<HealthSummaryQueryResponse> result =
                healthQueryService
                        .getHealthSummaryList(userId);

        // then
        assertEquals(expected, result);
        assertEquals(2, result.size());

        verify(healthQueryRepository)
                .findHealthSummaryList(userId);
    }

    @Test
    @DisplayName("Health와 User 소유 관계 확인 성공")
    void checkHealthWithUserSuccess() {

        // given
        Long healthId = 1L;
        Long userId = 1L;

        when(healthQueryRepository
                .existsByHealthIdAndUserId(
                        healthId,
                        userId
                ))
                .thenReturn(true);

        // when
        boolean result =
                healthQueryService.checkHealthWithUser(
                        healthId,
                        userId
                );

        // then
        assertTrue(result);

        verify(healthQueryRepository)
                .existsByHealthIdAndUserId(
                        healthId,
                        userId
                );
    }

    @Test
    @DisplayName("Health와 User 소유 관계 확인 실패")
    void checkHealthWithUserFail() {

        // given
        Long healthId = 1L;
        Long userId = 2L;

        when(healthQueryRepository
                .existsByHealthIdAndUserId(
                        healthId,
                        userId
                ))
                .thenReturn(false);

        // when
        boolean result =
                healthQueryService.checkHealthWithUser(
                        healthId,
                        userId
                );

        // then
        assertFalse(result);

        verify(healthQueryRepository)
                .existsByHealthIdAndUserId(
                        healthId,
                        userId
                );
    }
}