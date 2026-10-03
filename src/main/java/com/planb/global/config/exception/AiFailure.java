package com.planb.global.config.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 동일 요청 재시도 가능 여부를 포함한 AI 오케스트레이션 실패 사유
 */
@Getter
@RequiredArgsConstructor
public enum AiFailure {

    // AI 호출 자체가 실패했다. (네트워크, 모델 오류)
    UPSTREAM_CALL_FAILED(
            true,
            AiExceptionEnum.AI_TEMPORARILY_UNAVAILABLE,
            "AI 호출에 실패했습니다."
    ),

    // 빈 AI 응답 본문
    RESPONSE_EMPTY(
            true,
            AiExceptionEnum.AI_TEMPORARILY_UNAVAILABLE,
            "AI 구조화 응답이 비어 있습니다."
    ),

    // AI 응답의 스키마 객체 변환 실패
    RESPONSE_UNPARSABLE(
            true,
            AiExceptionEnum.AI_TEMPORARILY_UNAVAILABLE,
            "AI 구조화 응답을 해석하지 못했습니다."
    ),

    // 스키마 통과 후 도메인 검증 실패
    RESPONSE_INVALID(
            true,
            AiExceptionEnum.AI_RESPONSE_REJECTED,
            "AI 구조화 응답 검증에 실패했습니다."
    ),

    // 교정 후에도 반복된 무효 AI 응답과 재시도 효과 없음
    RESPONSE_REPEATED_INVALID(
            false,
            AiExceptionEnum.AI_RESPONSE_REJECTED,
            "AI가 동일한 무효 응답을 반복했습니다."
    ),

    // 요청 컨텍스트 직렬화 실패, 서버 결함으로 재시도 제외
    CONTEXT_SERIALIZATION_FAILED(
            false,
            AiExceptionEnum.AI_INTERNAL_ERROR,
            "AI Context 직렬화를 실패하였습니다."
    );

    private final boolean retryable;
    private final AiExceptionEnum apiError;
    private final String description;

    public boolean isRetryable() {
        return retryable;
    }
}
