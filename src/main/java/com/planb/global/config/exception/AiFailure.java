package com.planb.global.config.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 오케스트레이션의 실패 사유.
 *
 * retryable은 같은 요청을 그대로 다시 보냈을 때 결과가 달라질 수 있는지를 뜻한다.
 * 재시도 판단은 이 플래그만 보고 하며, 예외 타입이나 메시지 문자열로 하지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum AiFailure {

    // AI 호출 자체가 실패했다. (네트워크, 모델 오류)
    UPSTREAM_CALL_FAILED(true, AiExceptionEnum.AI_TEMPORARILY_UNAVAILABLE,
            "AI 호출에 실패했습니다."),

    // 응답 본문이 비어 있다.
    RESPONSE_EMPTY(true, AiExceptionEnum.AI_TEMPORARILY_UNAVAILABLE,
            "AI 구조화 응답이 비어 있습니다."),

    // 응답을 스키마에 맞는 객체로 변환하지 못했다.
    RESPONSE_UNPARSABLE(true, AiExceptionEnum.AI_TEMPORARILY_UNAVAILABLE,
            "AI 구조화 응답을 해석하지 못했습니다."),

    // 스키마는 맞지만 도메인 검증을 통과하지 못했다.
    RESPONSE_INVALID(true, AiExceptionEnum.AI_RESPONSE_REJECTED,
            "AI 구조화 응답 검증에 실패했습니다."),

    // 교정 요청에도 같은 무효 응답을 반복했다. 재시도해도 같은 결과가 나온다.
    RESPONSE_REPEATED_INVALID(false, AiExceptionEnum.AI_RESPONSE_REJECTED,
            "AI가 동일한 무효 응답을 반복했습니다."),

    // 요청 컨텍스트를 직렬화하지 못했다. 서버 결함이므로 재시도 대상이 아니다.
    CONTEXT_SERIALIZATION_FAILED(false, AiExceptionEnum.AI_INTERNAL_ERROR,
            "AI Context 직렬화를 실패하였습니다.");

    private final boolean retryable;
    private final AiExceptionEnum apiError;
    private final String description;

    public boolean isRetryable() {
        return retryable;
    }
}
