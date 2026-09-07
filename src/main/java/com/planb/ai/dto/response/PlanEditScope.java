package com.planb.ai.dto.response;

import java.util.List;

public record PlanEditScope(List<Integer> rebuildDayNumbers, boolean preserveOtherDays) {
    public PlanEditScope(List<Integer> rebuildDayNumbers) {

        this(rebuildDayNumbers, true);
    }
}
