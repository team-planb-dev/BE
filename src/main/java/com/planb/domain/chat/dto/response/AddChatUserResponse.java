package com.planb.domain.chat.dto.response;

public record AddChatUserResponse(String username,
                                  Long chatRoomId,
                                  String message) {
}
