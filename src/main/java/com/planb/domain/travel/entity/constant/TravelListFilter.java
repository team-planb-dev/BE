package com.planb.domain.travel.entity.constant;

/**
 * 여행 목록 화면의 탭 구분.
 *
 * UPCOMING 탭은 배지 기준의 예정과 여행 중을 함께 보여주므로 종료일이 오늘 이후인 여행을 모두 포함한다.
 */
public enum TravelListFilter {

    UPCOMING,
    PAST
}
