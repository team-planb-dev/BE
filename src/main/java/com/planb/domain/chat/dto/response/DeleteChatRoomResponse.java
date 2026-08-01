package com.planb.domain.chat.dto.response;

public record DeleteChatRoomResponse (Long chatRoomId,
                                      String chatRoomName,
                                      String chatRoomDescription,
                                      String message){

}
