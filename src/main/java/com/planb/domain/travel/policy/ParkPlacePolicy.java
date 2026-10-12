package com.planb.domain.travel.policy;

/**
 * 검색 원본 분류에 따른 공원 산책 후보 자격
 */
public final class ParkPlacePolicy {

    private ParkPlacePolicy() {
    }

    public static boolean allows(
            String type,
            String categoryName
    ) {

        return ("AT4".equals(type) || "12".equals(type))
                && categoryName != null && categoryName.contains("공원");
    }
}
