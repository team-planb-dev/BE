package com.planb.global.config.exception.domain;

import com.planb.global.config.exception.BaseExceptionEnum;

import java.io.Serial;

public class ForbiddenException extends BaseException{

    @Serial
    private static final long serialVersionUID = -5148452097821358350L;

    public ForbiddenException() {
        super(BaseExceptionEnum.FORBIDDEN, new Object[]{"인증 실패"});
    }

    public ForbiddenException(Object[] message) {
        super(BaseExceptionEnum.FORBIDDEN, message);
    }
}
