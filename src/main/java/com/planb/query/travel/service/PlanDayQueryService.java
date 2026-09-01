package com.planb.query.travel.service;

import com.planb.query.travel.dto.response.PlanDayQueryResponse;
import com.planb.query.travel.repository.PlanDayQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanDayQueryService {

    private final PlanDayQueryRepository planDayQueryRepository;

    // PlanId로 PlanDay 객체 리스트 가져오기
    public List<PlanDayQueryResponse> getPlanDaysByPlanId(Long planId){
        return planDayQueryRepository.findPlanDaysByPlanId(planId);
    }
}
