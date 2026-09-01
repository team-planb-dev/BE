package com.planb.query.travel.service;

import com.planb.query.travel.dto.response.PlanQueryResponse;
import com.planb.query.travel.repository.PlanQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlanQueryService {

    private final PlanQueryRepository planQueryRepository;

    // travelId로 Plan객체 조회하기
    public PlanQueryResponse getPlanByTravelId(Long travelId){
        return planQueryRepository.findPlanByTravelId(travelId);
    }
}
