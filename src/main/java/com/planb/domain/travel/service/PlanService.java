package com.planb.domain.travel.service;

import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.domain.travel.dto.request.CreatePlanRequest;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanService {

    /*
    Repository
     */
    private final PlanRepository planRepository;

    /*
    Handler
     */
    private final TravelRecommendHandler travelRecommendHandler;

    public Plan createPlan(CreatePlanRequest createPlanRequest){

        return Plan
                .builder()
                .planName(createPlanRequest
                        .planName())
                .travel(createPlanRequest
                        .travel())
                .build();
    }

    // AI로 사용자 입력 및 정보기반 일정 생성하기
    public CreatePlanAiResponse makePlanByAi(TravelPlanContext travelPlanContext){

        return travelRecommendHandler.createPlanByAi(travelPlanContext);
    }

    /*
    기본 CRUD 모음
     */
    public void savePlan(Plan plan){
        planRepository.save(plan);
    }
}
