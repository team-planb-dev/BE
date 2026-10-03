package com.planb.global.config.exception.domain;

import com.planb.global.config.exception.AiFailure;
import lombok.Getter;

/**
 * 실패 사유를 포함한 AI 오케스트레이션 예외
 */
@Getter
public class AiOrchestrationException extends RuntimeException {

    private final AiFailure failure;

    public AiOrchestrationException(
            AiFailure failure,
            String detail
    ) {

        super(detail == null ? failure.getDescription() : failure.getDescription() + " " + detail);
        this.failure = failure;
    }

    public AiOrchestrationException(
            AiFailure failure,
            Throwable cause
    ) {

        super(failure.getDescription(), cause);
        this.failure = failure;
    }

    public AiOrchestrationException(AiFailure failure) {

        super(failure.getDescription());
        this.failure = failure;
    }
}
