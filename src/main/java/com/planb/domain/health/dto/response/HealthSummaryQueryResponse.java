package com.planb.domain.health.dto.response;

import com.planb.domain.health.entity.constant.DiseaseType;

import java.util.List;

public record HealthSummaryQueryResponse(Long healthId,
                                         String travelerName,
                                         boolean hasMedication,
                                         List<DiseaseType> diseaseTypes,
                                         boolean hasAllergy) {
}
