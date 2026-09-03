package com.planb.domain.travel.repository;

import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.PlanDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlanDayRepository extends JpaRepository<PlanDay,Long> {

    List<PlanDay> findAllByPlan(Plan plan);

    void deleteAllByPlan(Plan plan);
}
