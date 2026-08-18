package com.planb.domain.health.repository;

import com.planb.domain.health.entity.MedicationInfo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MedicationInfoRepository extends JpaRepository<MedicationInfo,Long> {
}
