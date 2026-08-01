package com.planb.domain.chat.websocket.resolver;

import org.springframework.stereotype.Component;

@Component
public class ChatDestinationResolver {

    private static final String CHAT_SUBSCRIPTION_PREFIX =
            "/sub/api/v1/chat/";

    public Long extractRoomId(String destination){
        if(destination == null||
                !destination.startsWith(CHAT_SUBSCRIPTION_PREFIX)){
                return null;
            }

        String roomIdValue = destination
                .substring(CHAT_SUBSCRIPTION_PREFIX.length());

        if(roomIdValue.isBlank()||roomIdValue.contains("/")){
            return null;
        }

        try{
            return Long.parseLong(roomIdValue);
        }catch(NumberFormatException e){
            return null;
        }
    }
}
