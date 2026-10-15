package com.planb.domain.chat.dto.request;

import com.planb.domain.chat.dto.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;

public record SendChatMessageRequest(
        @Schema(description = "전송 유형입니다. TALK, CONFIRM, CANCEL 중 하나를 전달해 주세요.", example = "TALK")
        MessageType type,

        @Schema(description = "TALK 유형의 자연어 메시지입니다. TALK일 때 필수입니다.", example = "첫째 날 카페를 다른 곳으로 바꿔 주세요.")
        String message,
        @Schema(description = "확정·취소할 수정 요청 식별값, 생략 시 현재 요청")
        String editRequestId
) {

    public SendChatMessageRequest(MessageType type, String message) {
        this(type, message, null);
    }

}
