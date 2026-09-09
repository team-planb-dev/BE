package com.planb.global.config.exception;

import com.planb.global.enums.MessageCommInterface;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum TravelExceptionEnum implements MessageCommInterface {

    COMPANION_REQUIRED("TRAVEL.EXCEPTION.COMPANION_REQUIRED",
            "이번 여행에 참여할 구성원을 한 명 이상 선택해야 합니다."),

    COMPANION_NOT_OWNED("TRAVEL.EXCEPTION.COMPANION_NOT_OWNED",
            "선택한 구성원을 찾을 수 없습니다."),

    TRAVEL_NOT_SAVED("TRAVEL.EXCEPTION.TRAVEL_NOT_SAVED",
            "저장하지 않은 일정은 공유할 수 없습니다.");

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
