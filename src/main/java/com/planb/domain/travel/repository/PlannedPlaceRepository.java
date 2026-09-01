package com.planb.domain.travel.repository;

import com.planb.domain.travel.entity.PlannedPlace;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlannedPlaceRepository extends JpaRepository<PlannedPlace,Long> {
}
