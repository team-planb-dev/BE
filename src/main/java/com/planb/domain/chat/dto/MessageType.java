package com.planb.domain.chat.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        description = "메시지 유형입니다. 클라이언트는 TALK, CONFIRM, CANCEL만 전송하며 ENTER와 LEAVE는 서버가 발행합니다."
)
public enum MessageType {

    ENTER, TALK, CONFIRM, CANCEL, LEAVE
}
