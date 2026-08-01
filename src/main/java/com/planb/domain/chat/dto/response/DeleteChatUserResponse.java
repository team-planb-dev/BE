package com.planb.domain.chat.dto.response;

public record DeleteChatUserResponse(Long chatRoomId,
                                     String username,
                                     String message) {
}
