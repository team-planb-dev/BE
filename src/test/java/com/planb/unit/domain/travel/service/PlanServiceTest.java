package com.planb.unit.domain.travel.service;

import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.domain.travel.dto.request.CreatePlanRequest;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.repository.PlanRepository;
import com.planb.domain.travel.service.PlanService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @Mock
    private TravelRecommendHandler travelRecommendHandler;

    @InjectMocks
    private PlanService planService;

    @Test
    @DisplayName("Plan 객체 생성")
    void createPlan() {

        Travel travel =
                Travel.builder()
                        .travelName("부산 여행")
                        .build();

        CreatePlanRequest request =
                new CreatePlanRequest(
                        travel,
                        "부산 여행 일정"
                );

        Plan plan =
                planService.createPlan(
                        request
                );

        assertEquals(
                "부산 여행 일정",
                plan.getPlanName()
        );

        assertSame(
                travel,
                plan.getTravel()
        );
    }

    @Test
    @DisplayName("AI 기반 여행 일정 생성")
    void makePlanByAi() {

        TravelPlanContext context =
                org.mockito.Mockito.mock(
                        TravelPlanContext.class
                );

        CreatePlanAiResponse response =
                org.mockito.Mockito.mock(
                        CreatePlanAiResponse.class
                );

        when(
                travelRecommendHandler.createPlanByAi(
                        context
                )
        ).thenReturn(response);

        CreatePlanAiResponse result =
                planService.makePlanByAi(
                        context
                );

        assertSame(
                response,
                result
        );

        verify(travelRecommendHandler)
                .createPlanByAi(context);
    }

    @Test
    @DisplayName("Plan 객체 저장")
    void savePlan() {

        Plan plan =
                Plan.builder()
                        .planName("부산 여행 일정")
                        .build();

        planService.savePlan(
                plan
        );

        verify(planRepository)
                .save(plan);
    }
}