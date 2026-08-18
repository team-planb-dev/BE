package com.planb.query.health.service;


import com.planb.query.health.repository.MedicationInfoQueryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MedicationInfoQueryService {

    private final MedicationInfoQueryRepository medicationInfoQueryRepository;

    public void deleteAllMedicationInfoByHealthId(Long healthId){
        medicationInfoQueryRepository.deleteAllByHealthId(healthId);
    }
}
