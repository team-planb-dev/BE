package com.planb.global.docs.controller.chat;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.dto.response.SendChatMessageResponse;

import java.time.Instant;

@Tag(
        name = "Chat WebSocket",
        description = """
                문서화를 위한 Mock API이며 실제 통신은 WebSocket/STOMP를 사용합니다.
                Swagger의 `/mock/api/v1/chat/{roomId}/send` 경로로 메시지를 전송하지 마세요.

                연결 순서
                1. WebSocket endpoint `/ws-stomp`에 연결해 주세요.
                2. STOMP CONNECT native header에 `Authorization: Bearer {accessToken}`을 전달해 주세요.
                3. 메시지를 받으려면 `/sub/api/v1/chat/{roomId}`를 SUBSCRIBE해 주세요.
                4. 메시지를 보내려면 `/pub/api/v1/chat/{roomId}/send`로 SEND해 주세요.
                5. 서버는 `/sub/api/v1/chat/{roomId}`로 메시지를 발행합니다.
                6. 사용을 마치면 STOMP DISCONNECT 후 WebSocket 연결을 종료해 주세요.

                CONNECT에서 Access Token을 검증합니다. SUBSCRIBE와 SEND에서는 destination 형식과
                채팅방 멤버 여부를 검증합니다. destination 형식이 잘못되면
                `WEBSOCKET.EXCEPTION.CHATROOM_NOT_FOUND`를 반환합니다.
                채팅방이 없거나 사용자가 멤버가 아니면
                `WEBSOCKET.EXCEPTION.SUBSCRIBER_NOT_MATCHED`로 요청을 거부합니다.
                """
)
@RestController
@RequestMapping("/mock/api/v1/chat")
public class ChatWebSocketMockController {

    @Operation(
            summary = "채팅 메시지 전송",
            description = """
                    실제 메시지는 `/pub/api/v1/chat/{roomId}/send`로 STOMP SEND해 주세요.
                    Swagger에 표시된 HTTP 경로는 요청 및 발행 payload 확인용이며 실제 HTTP API가 아닙니다.

                    클라이언트는 `TALK`, `CONFIRM`, `CANCEL` 유형만 전송할 수 있습니다.
                    `TALK`은 자연어 메시지가 필수이며 여행 채팅방에서는 AI 일정 수정 미리보기가 생성될 수 있습니다.
                    `CONFIRM`은 저장된 수정안을 일정에 반영하고, `CANCEL`은 수정안을 폐기합니다.
                    `ENTER`는 세션의 새 SUBSCRIBE가 등록될 때 서버가 발행합니다.
                    `LEAVE`는 UNSUBSCRIBE 또는 DISCONNECT로 등록된 구독이 해제될 때 서버가 발행합니다.

                    SEND가 처리되면 서버가 같은 채팅방의
                    `/sub/api/v1/chat/{roomId}` 구독자에게 `SendChatMessageResponse`를 발행합니다.
                    """
    )
    @Parameter(
            name = "Authorization",
            description = "로그인 후 발급받은 Access Token입니다. STOMP CONNECT native header에 전달해 주세요.",
            required = true,
            in = ParameterIn.HEADER,
            example = "Bearer eyJhbGciOiJIUzI1NiJ9..."
    )
    @ApiResponse(
            responseCode = "200",
            description = "서버가 Subscribe destination으로 발행하는 메시지 예시",
            content = @Content(
                    schema = @Schema(
                            implementation = SendChatMessageResponse.class
                    ),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "type": "TALK",
                                      "roomId": 1,
                                      "senderId": 1,
                                      "senderNickname": "wooju",
                                      "message": "안녕하세요.",
                                      "editPreview": null,
                                      "sendTime": "2026-07-31T09:00:00Z"
                                    }
                                    """
                    )
            )
    )
    @PostMapping("/{roomId}/send")
    public ResponseEntity<SendChatMessageResponse> publishMessage(
            @Parameter(
                    description = "메시지를 보내고 구독할 채팅방 ID",
                    example = "1"
            )
            @PathVariable Long roomId,
            @RequestBody SendChatMessageRequest request
    ) {

        SendChatMessageResponse response = new SendChatMessageResponse(
                MessageType.TALK,
                roomId,
                1L,
                "wooju",
                "테스트 메시지",
                null,
                Instant.now()
        );

        return ResponseEntity.ok(response);
    }
}
