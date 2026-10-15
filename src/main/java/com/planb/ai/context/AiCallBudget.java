package com.planb.ai.context;

import com.planb.global.config.exception.AiFailure;
import com.planb.global.config.exception.domain.AiOrchestrationException;

/**
 * 요청 전체의 모델 시도와 Tool 실행 상한
 */
public final class AiCallBudget {

    private final int modelLimit;
    private final int roundLimit;
    private final int toolLimit;
    private int modelCalls;
    private int toolRounds;
    private int toolCalls;

    public AiCallBudget() {

        this(
                32,
                24,
                128
        );
    }

    public AiCallBudget(
            int modelLimit,
            int roundLimit,
            int toolLimit
    ) {

        if (modelLimit < 1 || roundLimit < 0 || toolLimit < 0) {
            throw new IllegalArgumentException("Invalid AI call budget");
        }

        this.modelLimit = modelLimit;
        this.roundLimit = roundLimit;
        this.toolLimit = toolLimit;
    }

    public synchronized void reserveModel() {

        if (modelCalls >= modelLimit) {
            throw new AiOrchestrationException(AiFailure.BUDGET_EXCEEDED, "model calls");
        }

        modelCalls++;
    }

    public synchronized void reserveTools(int count) {

        if (count < 1 || toolRounds >= roundLimit || count > toolLimit - toolCalls) {
            throw new AiOrchestrationException(AiFailure.BUDGET_EXCEEDED, "tool calls");
        }

        toolRounds++;
        toolCalls += count;
    }
}
