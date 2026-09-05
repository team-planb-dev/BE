package com.planb.global.config.exception;

import lombok.RequiredArgsConstructor;
import com.planb.global.enums.MessageCommInterface;

@RequiredArgsConstructor
public enum WebSocketExceptionEnum implements MessageCommInterface {
    CHATROOM_NOT_FOUND("WEBSOCKET.EXCEPTION.CHATROOM_NOT_FOUND","해당 채팅방을 찾을 수 없습니다."),
    SUBSCRIBER_NOT_MATCHED("WEBSOCKET.EXCEPTION.SUBSCRIBER_NOT_MATCHED","해당 사용자는 구독자가 아닙니다."),
    USER_ROOM_DUPLICATED("WEBSOCKET.EXCEPTION.USER_ROOM_DUPLICATED","해당 유저는 이미 해당 방과 등록 되어있습니다."),
    TRAVEL_NOT_LINKED("WEBSOCKET.EXCEPTION.TRAVEL_NOT_LINKED","해당 채팅방은 여행과 연결되어 있지 않습니다.");


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
