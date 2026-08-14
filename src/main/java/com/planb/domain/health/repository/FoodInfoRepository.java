package com.planb.domain.health.repository;

import com.planb.domain.health.entity.FoodInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FoodInfoRepository extends JpaRepository<FoodInfo,Long> {
}
