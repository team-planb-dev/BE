package com.planb.unit.query.health.service;

import com.planb.query.health.repository.MedicationInfoQueryRepository;
import com.planb.query.health.service.MedicationInfoQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

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
                .deleteAllMedicationInfoByHealthId(healthId);

        // then
        verify(medicationInfoQueryRepository)
                .deleteAllByHealthId(healthId);
    }
}