package com.planb.domain.travel.entity.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

/**
 * 여행 목록에서 보여주는 진행 상태 배지.
 *
 * 별도 컬럼으로 저장하지 않고 조회 시점의 날짜와 여행 기간을 비교해 계산한다.
 * 저장해두면 날짜가 지날 때마다 갱신하는 배치가 필요해진다.
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
