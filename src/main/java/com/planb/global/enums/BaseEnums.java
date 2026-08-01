package com.planb.global.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;


public class BaseEnums {

    @Getter
    @RequiredArgsConstructor
    public enum Default implements MessageCommInterface {
        SUCCESS("BASE.DEFAULT.SUCCESS","성공"),
        FAILED("BASE.DEFAULT.FAILED","실패");

        private final String code;
        private final String message;
    }
}
