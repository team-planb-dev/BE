package com.planb.domain.travel.entity.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

/**
 * 조회 시점에 계산하는 여행 진행 상태
 */
@Getter
@RequiredArgsConstructor
public enum TravelStatus {

    UPCOMING("예정"),
    ONGOING("여행 중"),
    COMPLETED("완료");

    private final String label;

    public static TravelStatus of(
            LocalDate today,
            LocalDate startDate,
            LocalDate endDate
    ) {

        if (endDate.isBefore(today)) {
            return COMPLETED;
        }

        if (startDate.isAfter(today)) {
            return UPCOMING;
        }

        return ONGOING;
    }
}
