package com.planb.global.config.exception.dto;

import com.planb.global.enums.MessageCommInterface;

import java.io.Serializable;

public record ApiResult<T>(
        boolean success,
        T data,
        Error error
) implements Serializable {

    public static <T> ApiResult<T> successNoContent() {
        return new ApiResult<>(true, null, null);
    }

    public static <T> ApiResult<T> failNoContent() {
        return new ApiResult<>(false, null, null);
    }

    public static <T> ApiResult<T> success(T data) {
        return new ApiResult<>(true, data, null);
    }

    public static <T> ApiResult<T> fail(MessageCommInterface messageCommInterface, T data) {
        return new ApiResult<>(false, data, new Error(messageCommInterface.getCode(), messageCommInterface.getMessage()));
    }

    public static <T> ApiResult<T> fail(String errorCode, String message) {
        return new ApiResult<>(false, null, new Error(errorCode, message));
    }

    public static <T> ApiResult<T> fail(String errorCode, String message, T data) {
        return new ApiResult<>(false, data, new Error(errorCode, message));
    }

    public static <T> ApiResult<T> fail(MessageCommInterface messageCommInterface) {
        return new ApiResult<>(false, null, new Error(messageCommInterface.getCode(), messageCommInterface.getMessage()));
    }

    public record Error(
            String errorCode,
            String message
    ) {
    }
}
