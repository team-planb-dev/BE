package com.planb.ai.dto.response;

import java.util.List;

public record PlanEditScope(
        List<Integer> rebuildDayNumbers,
        boolean preserveOtherDays,
        List<Integer> densityReductionDayNumbers
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
}
