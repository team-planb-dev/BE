package com.planb.domain.chat.dto.request;

import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.chat.entity.ChatRoomMember;
import com.planb.domain.user.entity.User;

public record DeleteChatUserRequest (ChatRoomMember chatRoomMember,
                                    ChatRoom chatRoom,
                                    User user){
}
