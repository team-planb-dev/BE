package com.planb.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record DeleteChatRoomRequest(
        @Schema(description = "삭제할 채팅방 ID", example = "1")
        Long roomId
) {
}
