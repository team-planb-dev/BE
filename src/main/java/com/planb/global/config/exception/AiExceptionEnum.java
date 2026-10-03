package com.planb.global.config.exception;

import com.planb.global.enums.MessageCommInterface;
import lombok.RequiredArgsConstructor;

/**
 * 클라이언트에 노출하는 AI 오케스트레이션 실패 유형
 */
@RequiredArgsConstructor
public enum AiExceptionEnum implements MessageCommInterface {

    // 동일 요청 재시도로 성공 가능한 일시적 실패
    AI_TEMPORARILY_UNAVAILABLE("AI.EXCEPTION.AI_TEMPORARILY_UNAVAILABLE",
            "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요."),

    // 규격에 맞지 않는 AI 일정과 동일 요청 재시도 결과의 불확실성
    AI_RESPONSE_REJECTED("AI.EXCEPTION.AI_RESPONSE_REJECTED",
            "AI가 요청 조건을 만족하는 결과를 만들지 못했습니다."),

    // 클라이언트 조치가 불가능한 서버 내부 결함
    AI_INTERNAL_ERROR("AI.EXCEPTION.AI_INTERNAL_ERROR",
            "AI 요청 처리 중 서버 오류가 발생했습니다.");

    private final String errorCode;
    private final String message;

    @Override
    public String getCode() {
        return errorCode;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
