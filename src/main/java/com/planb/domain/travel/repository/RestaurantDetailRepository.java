package com.planb.domain.travel.repository;

import com.planb.domain.travel.entity.RestaurantDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RestaurantDetailRepository extends JpaRepository<RestaurantDetail,Long> {
}
