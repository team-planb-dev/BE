package com.planb.query.travel.service;

import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.query.travel.repository.PlanScheduleQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanScheduleQueryService {

    private final PlanScheduleQueryRepository planScheduleQueryRepository;

    // PlanDayId 리스트로 PlanSchedule 객체 리스트 조회하기
    public List<PlanSchedule> getPlanSchedulesByPlanDayIds(List<Long> planDayIds){
        return planScheduleQueryRepository.findPlanSchedulesByPlanDayIds(planDayIds);
    }
}
