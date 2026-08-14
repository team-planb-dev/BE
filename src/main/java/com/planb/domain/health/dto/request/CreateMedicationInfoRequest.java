package com.planb.domain.health.dto.request;



import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.constant.MedicationBasis;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record CreateMedicationInfoRequest(
        Health health,
        List<MedicationInfoDetail> medicationInfoList
) {

    public record MedicationInfoDetail(
            String drugName,
            MedicationBasis medicationBasis,
            LocalTime medicationTime,
            Set<MealMedicationRuleDetail> mealMedicationRuleDetails
    ) {
    }
}