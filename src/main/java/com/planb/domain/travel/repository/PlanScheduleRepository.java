package com.planb.domain.travel.repository;

import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.entity.PlanSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanScheduleRepository extends JpaRepository<PlanSchedule,Long> {

    List<PlanSchedule> findAllByPlanDayIn(List<PlanDay> planDays);

    void deleteAllByPlanDayIn(List<PlanDay> planDays);
}
