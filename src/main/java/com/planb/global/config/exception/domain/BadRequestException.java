package com.planb.global.config.exception.domain;

import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.enums.MessageCommInterface;

import java.io.Serial;

public class BadRequestException extends BaseException{

    @Serial
    private static final long serialVersionUID = -5148452197821358350L;

    public BadRequestException() {
        super(BaseExceptionEnum.BAD_REQUEST);
    }

    public BadRequestException(MessageCommInterface messageCommInterface) {
        super(messageCommInterface);
    }
}
