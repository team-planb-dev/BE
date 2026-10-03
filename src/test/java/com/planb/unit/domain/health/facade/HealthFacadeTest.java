package com.planb.unit.domain.health.facade;

import com.planb.domain.health.dto.request.AddCompanionRequest;
import com.planb.domain.health.dto.request.DeleteCompanionRequest;
import com.planb.domain.health.dto.response.AddCompanionResponse;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.facade.HealthFacade;
import com.planb.domain.health.service.FoodInfoService;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.health.service.MedicationInfoService;
import com.planb.domain.user.entity.User;
import com.planb.global.config.exception.HealthExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.query.health.service.FoodInfoQueryService;
import com.planb.query.health.service.HealthQueryService;
import com.planb.query.health.service.MedicationInfoQueryService;
import com.planb.query.user.service.UserQueryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthFacadeTest {

    @Mock
    private HealthService healthService;

    @Mock
    private FoodInfoService foodInfoService;

    @Mock
    private MedicationInfoService medicationInfoService;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private HealthQueryService healthQueryService;

    @Mock
    private FoodInfoQueryService foodInfoQueryService;

    @Mock
    private MedicationInfoQueryService medicationInfoQueryService;

    @InjectMocks
    private HealthFacade healthFacade;

    @Test
    @DisplayName("동행인 등록 순서")
    void addCompanionOrder() {

        String username = "test@test.com";
        AddCompanionRequest request = mock(AddCompanionRequest.class);
        User user = mock(User.class);
        Health health = mock(Health.class);
        AddCompanionResponse response = mock(AddCompanionResponse.class);

        when(userQueryService.findByUsername(username))
                .thenReturn(user);
        when(healthService.addCompanion(request, user))
                .thenReturn(health);
        when(healthService.addCompanionResponse(health))
                .thenReturn(response);

        AddCompanionResponse result = healthFacade.addCompanion(request, username);

        assertThat(result)
                .isSameAs(response);

        InOrder order = inOrder(
                userQueryService,
                healthService,
                foodInfoService,
                medicationInfoService
        );

        order
                .verify(userQueryService)
                .findByUsername(username);
        order
                .verify(healthService)
                .addCompanion(request, user);
        order
                .verify(foodInfoService)
                .saveForCompanion(request, health);
        order
                .verify(medicationInfoService)
                .saveForCompanion(request, health);
        order
                .verify(healthService)
                .addCompanionResponse(health);
    }

    @Test
    @DisplayName("동행인 삭제 순서")
    void deleteCompanionOrder() {

        DeleteCompanionRequest request = new DeleteCompanionRequest(10L);
        when(userQueryService.findUserIdInCache("test@test.com"))
                .thenReturn(1L);

        healthFacade.deleteCompanion(request, "test@test.com");

        InOrder order = inOrder(
                userQueryService,
                healthQueryService,
                foodInfoQueryService,
                medicationInfoQueryService,
                healthService
        );

        order
                .verify(userQueryService)
                .findUserIdInCache("test@test.com");
        order
                .verify(healthQueryService)
                .validateOwned(10L, 1L);
        order
                .verify(foodInfoQueryService)
                .deleteAllByHealthId(10L);
        order
                .verify(medicationInfoQueryService)
                .deleteAllMedicationInfoByHealthId(10L);
        order
                .verify(healthService)
                .deleteHealthById(10L);
        order
                .verify(healthService)
                .deleteCompanionResponse();
    }

    @Test
    @DisplayName("동행인 소유권 불일치 시 삭제 거부")
    void deleteCompanionNotOwner() {

        DeleteCompanionRequest request = new DeleteCompanionRequest(10L);
        when(userQueryService.findUserIdInCache("test@test.com"))
                .thenReturn(1L);
        doThrow(new BaseException(HealthExceptionEnum.HEALTH_NOT_FOUND))
                .when(healthQueryService)
                .validateOwned(10L, 1L);

        assertThatThrownBy(() -> healthFacade.deleteCompanion(request, "test@test.com"))
                .isInstanceOf(BaseException.class);

        verifyNoInteractions(
                foodInfoQueryService,
                medicationInfoQueryService,
                healthService
        );
    }
}
