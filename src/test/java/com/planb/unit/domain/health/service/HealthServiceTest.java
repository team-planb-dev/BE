package com.planb.unit.domain.health.service;

import com.planb.domain.health.dto.request.CreateHealthRequest;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.health.repository.HealthRepository;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    private HealthRepository healthRepository;

    @Mock
    private User user;

    private HealthService healthService;

    @BeforeEach
    void setUp() {
        healthService =
                new HealthService(healthRepository);
    }

    @Test
    @DisplayName("민감정보 동의 기반 전체 Health 객체 생성")
    void validSensitiveAgreeWithAgreeSuccess() {

        // given
        DiseaseType diseaseType =
                DiseaseType.DIABETES;

        WalkType walkType =
                WalkType.values()[0];

        CreateHealthRequest request =
                new CreateHealthRequest(
                        "강우주",
                        true,
                        true,
                        new CreateHealthRequest.HealthInfo(
                                diseaseType,
                                walkType
                        ),
                        new CreateHealthRequest.MealInfo(
                                true,
                                true,
                                LocalTime.of(8, 0),
                                true,
                                LocalTime.of(12, 0),
                                true,
                                LocalTime.of(18, 0)
                        )
                );

        // when
        Health result =
                healthService.validSensitiveAgree(
                        request,
                        user
                );

        // then
        assertEquals(
                "강우주",
                result.getTravelerName()
        );

        assertTrue(result.isSensitiveAgree());
        assertTrue(result.isHasMedication());

        assertNotNull(result.getHealthInfo());
        assertNotNull(result.getMealInfo());

        assertSame(
                user,
                result.getUser()
        );
    }

    @Test
    @DisplayName("민감정보 미동의 기반 최소 Health 객체 생성")
    void validSensitiveAgreeWithoutAgreeSuccess() {

        // given
        CreateHealthRequest request =
                new CreateHealthRequest(
                        "강우주",
                        false,
                        true,
                        null,
                        null
                );

        // when
        Health result =
                healthService.validSensitiveAgree(
                        request,
                        user
                );

        // then
        assertEquals(
                "강우주",
                result.getTravelerName()
        );

        assertFalse(result.isSensitiveAgree());
        assertFalse(result.isHasMedication());

        assertNull(result.getHealthInfo());
        assertNull(result.getMealInfo());

        assertSame(
                user,
                result.getUser()
        );
    }

    @Test
    @DisplayName("Health 객체 저장")
    void saveHealthSuccess() {

        // given
        Health health =
                Health.builder()
                        .travelerName("강우주")
                        .user(user)
                        .build();

        // when
        healthService.saveHealth(health);

        // then
        verify(healthRepository)
                .save(health);
    }

    @Test
    @DisplayName("Health ID 기반 객체 삭제")
    void deleteHealthByIdSuccess() {

        // given
        Long healthId = 1L;

        // when
        healthService.deleteHealthById(healthId);

        // then
        verify(healthRepository)
                .deleteById(healthId);
    }
}