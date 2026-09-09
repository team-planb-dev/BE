package com.planb.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record AddChatRoomMemberRequest(
        @Schema(description = "참여할 채팅방 ID", example = "1")
        Long roomId,

        @Schema(description = "로그인한 사용자의 ID", example = "1")
        Long userId
) {
}
