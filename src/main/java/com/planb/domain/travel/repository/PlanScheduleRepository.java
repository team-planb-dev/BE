package com.planb.domain.travel.repository;

import com.planb.domain.travel.entity.PlanSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanScheduleRepository extends JpaRepository<PlanSchedule,Long> {
}
