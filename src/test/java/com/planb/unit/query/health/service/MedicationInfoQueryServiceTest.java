package com.planb.unit.query.health.service;

import com.planb.query.health.repository.MedicationInfoQueryRepository;
import com.planb.query.health.service.MedicationInfoQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MedicationInfoQueryServiceTest {

    @Mock
    private MedicationInfoQueryRepository medicationInfoQueryRepository;

    private MedicationInfoQueryService medicationInfoQueryService;

    @BeforeEach
    void setUp() {
        medicationInfoQueryService =
                new MedicationInfoQueryService(
                        medicationInfoQueryRepository
                );
    }

    @Test
    @DisplayName("Health ID를 기준으로 모든 복약 정보 삭제")
    void deleteAllMedicationInfoByHealthIdSuccess() {

        // given
        Long healthId = 1L;

        // when
        medicationInfoQueryService
                .deleteAllMedicationInfoByHealthId(
                        healthId
                );

        // then
        verify(medicationInfoQueryRepository)
                .deleteAllByHealthId(
                        healthId
                );
    }

    @Test
    @DisplayName("User ID를 기준으로 모든 동행인의 복약 시간 조회")
    void getMedicationTimes() {

        // given
        Long userId = 1L;

        List<LocalTime> expected =
                List.of(
                        LocalTime.of(8, 0),
                        LocalTime.of(13, 0),
                        LocalTime.of(20, 0)
                );

        when(
                medicationInfoQueryRepository
                        .findMedicationTimesByUserId(
                                userId
                        )
        ).thenReturn(
                expected
        );

        // when
        List<LocalTime> result =
                medicationInfoQueryService
                        .getMedicationTimes(
                                userId
                        );

        // then
        assertThat(result)
                .isEqualTo(expected);

        verify(medicationInfoQueryRepository)
                .findMedicationTimesByUserId(
                        userId
                );
    }
}