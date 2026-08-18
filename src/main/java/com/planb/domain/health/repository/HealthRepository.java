package com.planb.domain.health.repository;

import com.planb.domain.health.entity.Health;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HealthRepository extends JpaRepository<Health,Long> {
}
