package com.planb.global.enums;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.planb.global.constant.serializer.MessageCommSerializer;

@JsonSerialize(using = MessageCommSerializer.class)
public interface MessageCommInterface {
    String getCode();
    String getMessage();

    default boolean isSuccess(){
        return this.getCode().equals(BaseEnums.Default.SUCCESS.getCode());
    }

    default boolean isFail(){
        return !this.getCode().equals(BaseEnums.Default.SUCCESS.getCode());
    }
}
