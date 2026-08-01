package com.planb.global.config.exception;

import lombok.RequiredArgsConstructor;
import com.planb.global.enums.MessageCommInterface;

@RequiredArgsConstructor
public enum BaseExceptionEnum implements MessageCommInterface {


    EXCEPTION_ISSUED("BASE.EXCEPTION.EXCEPTION_ISSUED", "시스템에러 발생, 관리자에게 문의하세요."),
    EXCEPTION_VALIDATION("BASE.EXCEPTION.EXCEPTION_VALIDATION", "요청 값 검증 실패"),
    ENTITY_NOT_FOUND("BASE.EXCEPTION.ENTITY_NOT_FOUND", "조회 대상이 없습니다."),
    FORBIDDEN("BASE.EXCEPTION.FORBIDDEN", "인증 실패 : {0}"),
    BAD_REQUEST("BASE.EXCEPTION.BAD_REQUEST", "잘못된 요청입니다."),
    ACCESS_DENIED("BASE.EXCEPTION.ACCESS_DENIED", "{0}"),
    JWT_EXPIRED("BASE.EXCEPTION.JWT_EXPIRED","JWT 토큰이 파기되었습니다."),
    INVALID_TOKEN_CATEGORY("BASE.EXCEPTION.INVALID_TOKEN_CATEGORY","해당 토큰의 카테고리는 적절하지 않습니다."),
    AUTH_SESSION_NOT_FOUND("BASE.EXCEPTION.AUTH_SESSION_NOT_FOUND","캐시에서 해당 유저를 찾을 수 없습니다."),
    REFRESH_TOKEN_NOT_FOUND("BASE.EXCEPTION.REFRESH_TOKEN_NOT_FOUND","refresh 토큰을 찾을 수 없습니다."),
    REFRESH_TOKEN_EXPIRED("BASE.EXCEPTION.REFRESH_TOKEN_EXPIRED","refresh 토큰이 파기되었습니다."),
    USER_ALREADY_LOGIN("BASE.EXCEPTION.USER_ALREADY_LOGIN","해당 유저는 이미 로그인 하였습니다."),
    USER_NOT_FOUND("BASE.EXCEPTION.USER_NOT_FOUND","해당 유저를 찾을 수 없습니다."),
    USER_UNAUTHORIZED("BASE.EXCEPTION.USER_UNAUTHORIZED","검증되지 않은 유저입니다."),
    INVALID_TOKEN("BASE.EXCEPTION.INVALID_TOKEN","잘못된 형식의 토큰입니다."),
    ;

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
