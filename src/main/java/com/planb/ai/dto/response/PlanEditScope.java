package com.planb.ai.dto.response;

import java.util.List;

public record PlanEditScope(
        List<Integer> rebuildDayNumbers,
        boolean preserveOtherDays,
        List<Integer> densityReductionDayNumbers,
        List<EditTarget> editTargets
) {

    public PlanEditScope(List<Integer> rebuildDayNumbers) {

        this(
                rebuildDayNumbers,
                true,
                List.of()
        );
    }

    public PlanEditScope(
            List<Integer> rebuildDayNumbers,
            boolean preserveOtherDays
    ) {

        this(
                rebuildDayNumbers,
                preserveOtherDays,
                List.of()
        );
    }

    public PlanEditScope(
            List<Integer> rebuildDayNumbers,
            boolean preserveOtherDays,
            List<Integer> densityReductionDayNumbers
    ) {

        this(
                rebuildDayNumbers,
                preserveOtherDays,
                densityReductionDayNumbers,
                List.of()
        );
    }

    // 날짜 전체 재구성 밖의 부분 변경 대상, 일차를 특정할 수 없으면 dayNumber 0
    public record EditTarget(
            Integer dayNumber,
            EditSlot slot,
            String placeName,
            Boolean menuOnly
    ) {

        public boolean menuOnlyRequested() {

            return Boolean.TRUE.equals(menuOnly);
        }
    }

    // 부분 변경 대상 슬롯 분류
    public enum EditSlot {

        BREAKFAST,
        LUNCH,
        DINNER,
        ATTRACTION,
        CAFE
    }
}
