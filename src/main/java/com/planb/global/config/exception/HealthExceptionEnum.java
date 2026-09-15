package com.planb.global.config.exception;

import com.planb.global.enums.MessageCommInterface;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum HealthExceptionEnum implements MessageCommInterface {

    HEALTH_NOT_FOUND("HEALTH.EXCEPTION.HEALTH_NOUT_FOUND", "해당 객체를 찾을 수 없습니다."),

    SENSITIVE_INFO_REQUIRED(
            "HEALTH.EXCEPTION.SENSITIVE_INFO_REQUIRED",
            "민감정보 수집에 동의한 경우 건강 정보와 식사 정보가 필요합니다.");

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
