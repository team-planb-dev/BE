package com.planb.domain.chat.dto.request;

import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.user.entity.User;

public record AddChatUserRequest(ChatRoom chatRoom,
                                 User user) {
}
