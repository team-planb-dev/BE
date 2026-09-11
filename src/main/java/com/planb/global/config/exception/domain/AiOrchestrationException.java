package com.planb.global.config.exception.domain;

import com.planb.global.config.exception.AiFailure;
import lombok.Getter;

/**
 * AI 오케스트레이션 실패를 사유와 함께 전달하는 예외.
 *
 * 호출부는 {@code getFailure().isRetryable()}로 재시도 여부를 판단하고,
 * 예외 핸들러는 {@code getFailure().getApiError()}로 클라이언트 코드를 정한다.
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
