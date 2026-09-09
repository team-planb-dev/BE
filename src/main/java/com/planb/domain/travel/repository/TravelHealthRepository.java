package com.planb.domain.travel.repository;

import com.planb.domain.travel.entity.TravelHealth;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TravelHealthRepository extends JpaRepository<TravelHealth, Long> {

    List<TravelHealth> findAllByTravelId(Long travelId);
}
