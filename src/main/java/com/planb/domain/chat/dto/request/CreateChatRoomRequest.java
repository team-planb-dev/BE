package com.planb.domain.chat.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateChatRoomRequest(
        @Schema(description = "생성할 채팅방 이름", example = "경주 여행 이야기")
        String chatRoomName
) {

}
