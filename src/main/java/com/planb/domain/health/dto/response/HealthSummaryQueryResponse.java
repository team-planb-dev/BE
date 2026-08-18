package com.planb.domain.health.dto.response;

import com.planb.domain.health.entity.constant.DiseaseType;

public record HealthSummaryQueryResponse(Long healthId,
                                         String travelerName,
                                         boolean hasMedication,
                                         DiseaseType diseaseType,
                                         boolean hasAllergy) {
}
