package com.planb.global.config.exception;

import com.planb.global.enums.MessageCommInterface;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum PlanEditExceptionEnum implements MessageCommInterface {

    MEAL_TIME_UNFEASIBLE("PLAN.EXCEPTION.MEAL_TIME_UNFEASIBLE", "이번에 확인한 식당 후보로 등록 식사 시간을 충족하지 못했습니다: {0}"),

    EDIT_REQUEST_EXPIRED("PLAN.EXCEPTION.EDIT_REQUEST_EXPIRED", "취소되었거나 새 요청으로 대체된 수정안입니다."),

    EDIT_RESULT_NOT_READY("PLAN.EXCEPTION.EDIT_RESULT_NOT_READY", "수정안을 생성 중입니다. 완료 후 다시 요청해 주세요."),

    EDIT_NOT_APPLIED("PLAN.EXCEPTION.EDIT_NOT_APPLIED", "일정 변경 미반영: {0}"),

    INVALID_AI_PLACE("PLAN.EXCEPTION.INVALID_AI_PLACE", "일정 장소 검증 실패: {0}"),

    ROUTE_LOOKUP_FAILED("PLAN.EXCEPTION.ROUTE_LOOKUP_FAILED", "이동 경로 조회 실패: {0}"),

    EDIT_TARGET_UNCLEAR("PLAN.EXCEPTION.EDIT_TARGET_UNCLEAR", "수정 대상 확인 필요: {0}"),

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
