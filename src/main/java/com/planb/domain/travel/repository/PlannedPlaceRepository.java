package com.planb.domain.travel.repository;

import com.planb.domain.travel.entity.PlannedPlace;
import com.planb.domain.travel.entity.Travel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlannedPlaceRepository extends JpaRepository<PlannedPlace,Long> {

    List<PlannedPlace> findAllByTravel(Travel travel);
}
