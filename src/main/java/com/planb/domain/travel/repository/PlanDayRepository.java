package com.planb.domain.travel.repository;

import com.planb.domain.travel.entity.PlanDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlanDayRepository extends JpaRepository<PlanDay,Long> {
}
