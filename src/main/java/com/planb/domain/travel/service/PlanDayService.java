package com.planb.domain.travel.service;

import com.planb.domain.travel.dto.request.CreatePlanDayRequest;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.repository.PlanDayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlanDayService {

    private final PlanDayRepository planDayRepository;

    public PlanDay createPlanDay(CreatePlanDayRequest createPlanDayRequest){

        return PlanDay
                .builder()
                .plan(createPlanDayRequest
                        .plan())
                .dayNumber(createPlanDayRequest
                        .dayNumber())
                .planDate(createPlanDayRequest
                        .planDate())
                .build();
    }

    /*
    기본 CRUD 모음
     */
    public void savePlanDay(PlanDay planDay){
        planDayRepository.save(planDay);
    }

    // 특정 Plan에 속한 PlanDay 리스트 조회하기
    public List<PlanDay> findAllByPlan(Plan plan){
        return planDayRepository.findAllByPlan(plan);
    }

    // 특정 Plan에 속한 PlanDay 리스트 일괄 삭제하기
    public void deleteAllByPlan(Plan plan){
        planDayRepository.deleteAllByPlan(plan);
    }

}
