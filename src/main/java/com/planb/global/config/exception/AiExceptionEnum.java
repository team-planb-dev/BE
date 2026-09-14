package com.planb.global.config.exception;

import com.planb.global.enums.MessageCommInterface;
import lombok.RequiredArgsConstructor;

/**
 * AI 오케스트레이션 실패가 클라이언트에 노출되는 형태.
 *
 * 도메인 실패 사유는 {@link AiFailure}에 있으며, 클라이언트는 대응 방법이
 * 다른 세 가지만 구분하면 된다.
 */
@RequiredArgsConstructor
public enum AiExceptionEnum implements MessageCommInterface {

    // 잠시 후 같은 요청을 다시 보내면 성공할 수 있다.
    AI_TEMPORARILY_UNAVAILABLE("AI.EXCEPTION.AI_TEMPORARILY_UNAVAILABLE",
            "AI 응답을 받지 못했습니다. 잠시 후 다시 시도해 주세요."),

    // AI가 규격에 맞는 일정을 만들지 못했다. 같은 요청을 반복해도 결과가 같을 수 있다.
    AI_RESPONSE_REJECTED("AI.EXCEPTION.AI_RESPONSE_REJECTED",
            "AI가 요청 조건을 만족하는 결과를 만들지 못했습니다."),

    // 서버 내부 결함이므로 클라이언트가 할 수 있는 조치가 없다.
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
