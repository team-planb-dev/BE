package com.planb.domain.travel.service;

import com.planb.domain.travel.dto.request.CreatePlanRequest;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanService {

    private final PlanRepository planRepository;

    public Plan createPlan(CreatePlanRequest createPlanRequest){

        return Plan
                .builder()
                .planName(createPlanRequest
                        .planName())
                .travel(createPlanRequest
                        .travel())
                .build();
    }
}
