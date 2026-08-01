package com.planb.global.config.exception.domain;

import lombok.Getter;
import com.planb.global.enums.MessageCommInterface;

import java.text.MessageFormat;

@Getter
public class BaseException extends RuntimeException {

    private final String errorCode;
    private final String message;

    public BaseException(MessageCommInterface messageCommInterface){
        super(messageCommInterface.getMessage());
        this.errorCode = messageCommInterface.getCode();
        this.message = messageCommInterface.getMessage();
    }

    public BaseException(MessageCommInterface messageCommInterface,
                         Object[] messageParameters){

        super(messageParameters != null
                ? MessageFormat.format(messageCommInterface.getMessage(), messageParameters)
                : messageCommInterface.getMessage());
        this.errorCode = messageCommInterface.getCode();
        this.message = messageParameters != null
                ? MessageFormat.format(messageCommInterface.getMessage(), messageParameters)
                : messageCommInterface.getMessage();

    }

    @Override
    public String getMessage() {
        return message;
    }

}
