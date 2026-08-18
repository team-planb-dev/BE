package com.planb.domain.health.dto.response;

import com.planb.domain.health.entity.constant.DiseaseType;

import java.util.List;

public record CompanionSummaryResponse(List<CompanionSummaryDetail> companionList) {

    // HealthSummaryQueryResponse객체를 해당 레코드 타입 객체로 변환하는 메소드
    public static CompanionSummaryResponse from(List<HealthSummaryQueryResponse> summaryList) {

        List<CompanionSummaryDetail> companionList = summaryList
                .stream()
                .map(summary -> new CompanionSummaryDetail(
                        summary
                                .healthId(),
                        summary
                                .travelerName(),
                        summary
                                .hasAllergy(),
                        summary
                                .hasMedication(),
                        summary
                                .diseaseType()
                ))
                .toList();

        return new CompanionSummaryResponse(companionList);

    }

    public record CompanionSummaryDetail(Long healthId,
                                         String travelerName,
                                         boolean hasAllergy,
                                         boolean hasMedication,
                                         DiseaseType diseaseType){
    }
}
