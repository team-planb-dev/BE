package com.planb.unit.domain.health.facade;

import com.planb.domain.health.dto.request.AddCompanionRequest;
import com.planb.domain.health.dto.request.CreateFoodInfoRequest;
import com.planb.domain.health.dto.request.CreateHealthRequest;
import com.planb.domain.health.dto.request.CreateMedicationInfoRequest;
import com.planb.domain.health.dto.request.DeleteCompanionRequest;
import com.planb.domain.health.dto.response.AddCompanionResponse;
import com.planb.domain.health.dto.response.DeleteCompanionResponse;
import com.planb.domain.health.entity.FoodInfo;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.MedicationInfo;
import com.planb.domain.health.facade.HealthFacade;
import com.planb.domain.health.service.FoodInfoService;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.health.service.MedicationInfoService;
import com.planb.domain.user.entity.User;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.query.health.service.FoodInfoQueryService;
import com.planb.query.health.service.HealthQueryService;
import com.planb.query.health.service.MedicationInfoQueryService;
import com.planb.query.user.service.UserQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HealthFacadeTest {

    @Mock
    private HealthService healthService;

    @Mock
    private FoodInfoService foodInfoService;

    @Mock
    private MedicationInfoService medicationInfoService;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private UserQueryService userQueryService;

    @Mock
    private HealthQueryService healthQueryService;

    @Mock
    private FoodInfoQueryService foodInfoQueryService;

    @Mock
    private MedicationInfoQueryService medicationInfoQueryService;

    @Mock
    private User user;

    @Mock
    private Health health;

    private HealthFacade healthFacade;

    @BeforeEach
    void setUp() {
        healthFacade =
                new HealthFacade(
                        healthService,
                        foodInfoService,
                        medicationInfoService,
                        userQueryService,
                        healthQueryService,
                        foodInfoQueryService,
                        medicationInfoQueryService
                );
    }

    @Test
    @DisplayName("민감정보 동의 기반 동행인 등록")
    void addCompanionWithSensitiveAgreeSuccess() {

        // given
        String username = "test@test.com";

        AddCompanionRequest request =
                mock(AddCompanionRequest.class);

        CreateHealthRequest healthRequest =
                mock(CreateHealthRequest.class);

        CreateFoodInfoRequest foodInfoRequest =
                mock(CreateFoodInfoRequest.class);

        CreateMedicationInfoRequest medicationInfoRequest =
                mock(CreateMedicationInfoRequest.class);

        List<FoodInfo> foodInfos =
                List.of(mock(FoodInfo.class));

        List<MedicationInfo> medicationInfos =
                List.of(mock(MedicationInfo.class));

        when(request.sensitiveAgree())
                .thenReturn(true);

        when(request.toHealthRequest())
                .thenReturn(healthRequest);

        when(healthRequest.travelerName())
                .thenReturn("동행인");

        when(userQueryService
                .findByUsername(username))
                .thenReturn(user);

        when(healthService
                .validSensitiveAgree(
                        healthRequest,
                        user
                ))
                .thenReturn(health);

        when(request
                .toFoodInfoRequest(health))
                .thenReturn(foodInfoRequest);

        when(request
                .toMedicationInfoRequest(health))
                .thenReturn(medicationInfoRequest);

        when(foodInfoService
                .makeFoodInfoList(
                        foodInfoRequest
                ))
                .thenReturn(foodInfos);

        when(medicationInfoService
                .makeMedicationInfoList(
                        medicationInfoRequest
                ))
                .thenReturn(medicationInfos);

        // when
        AddCompanionResponse result =
                healthFacade.addCompanion(
                        request,
                        username
                );

        // then
        assertNotNull(result);

        verify(healthService)
                .saveHealth(health);

        verify(foodInfoService)
                .saveFoodInfoAll(foodInfos);

        verify(medicationInfoService)
                .saveMedicationInfoAll(
                        medicationInfos
                );
    }

    @Test
    @DisplayName("민감정보 미동의 기반 최소 동행인 등록")
    void addCompanionWithoutSensitiveAgreeSuccess() {

        // given
        String username = "test@test.com";

        AddCompanionRequest request =
                mock(AddCompanionRequest.class);

        CreateHealthRequest healthRequest =
                mock(CreateHealthRequest.class);

        when(request.sensitiveAgree())
                .thenReturn(false);

        when(request.toHealthRequest())
                .thenReturn(healthRequest);

        when(healthRequest.travelerName())
                .thenReturn("동행인");

        when(userQueryService
                .findByUsername(username))
                .thenReturn(user);

        when(healthService
                .validSensitiveAgree(
                        healthRequest,
                        user
                ))
                .thenReturn(health);

        // when
        AddCompanionResponse result =
                healthFacade.addCompanion(
                        request,
                        username
                );

        // then
        assertNotNull(result);

        verify(healthService)
                .saveHealth(health);

        verifyNoInteractions(foodInfoService);
        verifyNoInteractions(medicationInfoService);
    }

    @Test
    @DisplayName("Health 소유권 검증 기반 동행인 삭제")
    void deleteCompanionSuccess() {

        // given
        String username = "test@test.com";

        Long userId = 1L;
        Long healthId = 10L;

        DeleteCompanionRequest request =
                new DeleteCompanionRequest(healthId);

        when(userQueryService
                .findByUsernameInCache(username)
                .userId())
                .thenReturn(userId);

        when(healthQueryService
                .checkHealthWithUser(
                        healthId,
                        userId
                ))
                .thenReturn(true);

        // when
        DeleteCompanionResponse result =
                healthFacade.deleteCompanion(
                        request,
                        username
                );

        // then
        assertNotNull(result);

        verify(foodInfoQueryService)
                .deleteAllByHealthId(healthId);

        verify(medicationInfoQueryService)
                .deleteAllMedicationInfoByHealthId(
                        healthId
                );

        verify(healthService)
                .deleteHealthById(healthId);
    }

    @Test
    @DisplayName("Health 소유권 불일치 기반 동행인 삭제 예외")
    void deleteCompanionNotOwnerException() {

        // given
        String username = "test@test.com";

        Long userId = 1L;
        Long healthId = 10L;

        DeleteCompanionRequest request =
                new DeleteCompanionRequest(healthId);

        when(userQueryService
                .findByUsernameInCache(username)
                .userId())
                .thenReturn(userId);

        when(healthQueryService
                .checkHealthWithUser(
                        healthId,
                        userId
                ))
                .thenReturn(false);

        // when & then
        assertThrows(
                BaseException.class,
                () -> healthFacade.deleteCompanion(
                        request,
                        username
                )
        );

        verifyNoInteractions(
                foodInfoQueryService
        );

        verifyNoInteractions(
                medicationInfoQueryService
        );

        verify(healthService, never())
                .deleteHealthById(anyLong());
    }
}