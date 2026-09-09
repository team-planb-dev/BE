package com.planb.domain.chat.dto.response;

import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;


public record SendChatMessageResponse(
        @Schema(description = "발행된 메시지 유형", example = "TALK")
        MessageType type,

        @Schema(description = "메시지가 발행된 채팅방 ID", example = "1")
        Long roomId,

        @Schema(description = "발신자 ID", example = "1")
        Long senderId,

        @Schema(description = "화면에 표시할 발신자 닉네임", example = "wooju")
        String senderNickname,

        @Schema(description = "발행된 메시지 본문", example = "안녕하세요.")
        String message,

        @Schema(description = "AI 일정 수정 미리보기입니다. 미리보기가 없는 메시지에는 null입니다.")
        EditPlanPreviewResponse editPreview,

        @Schema(description = "서버 메시지 발행 시각")
        Instant sendTime
) {


}
