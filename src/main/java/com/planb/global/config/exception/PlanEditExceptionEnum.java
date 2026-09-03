package com.planb.global.config.exception;

import com.planb.global.enums.MessageCommInterface;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PlanEditExceptionEnum implements MessageCommInterface {

    EDIT_RESULT_NOT_FOUND("PLAN.EXCEPTION.EDIT_RESULT_NOT_FOUND",
            "수정 요청 결과를 찾을 수 없거나 만료되었습니다.");

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
