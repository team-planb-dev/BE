package com.planb.global.config.exception;

import com.planb.global.enums.MessageCommInterface;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum AiExceptionEnum implements MessageCommInterface {

    AI_CONTEXT_SERIALIZATION_FAILED("AI.EXCEPTION.AI_CONTEXT_SERIALIZATION_FAILED",
            "AI Context 직렬화를 실패하였습니다.");

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
