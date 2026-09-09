package com.planb.global.config.exception;

import com.planb.global.enums.MessageCommInterface;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PlanEditExceptionEnum implements MessageCommInterface {

    EDIT_NOT_APPLIED("PLAN.EXCEPTION.EDIT_NOT_APPLIED", "일정 변경 미반영: {0}"),

    INVALID_AI_PLACE("PLAN.EXCEPTION.INVALID_AI_PLACE", "일정 장소 검증 실패: {0}"),

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
