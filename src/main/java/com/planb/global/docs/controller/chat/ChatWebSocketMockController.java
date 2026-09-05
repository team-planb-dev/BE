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
                STOMP 기반 실시간 채팅 API 명세입니다.

                공통 연결 정보
                - WebSocket Endpoint: `/ws-stomp`
                - Publish Destination: `/pub/api/v1/chat/{roomId}`
                - Subscribe Destination: `/sub/api/v1/chat/{roomId}`
                - 인증 방식: STOMP CONNECT Header에 `Authorization: Bearer {accessToken}` 전달

                아래 API는 Request/Response 구조를 Swagger에서 확인하기 위한
                Mock HTTP API이며 실제 HTTP 엔드포인트가 아닙니다.
                """
)
@RestController
@RequestMapping("/mock/api/v1/chat")
public class ChatWebSocketMockController {

    @Operation(
            summary = "채팅 메시지 전송",
            description = """
                    채팅방에 일반 메시지를 전송하는 STOMP 명세입니다.

                    인증 Header
                    - STOMP CONNECT Header
                    - `Authorization: Bearer {accessToken}`

                    Path Variable
                    - `roomId`: 메시지를 전송할 채팅방 ID

                    Swagger에 표시되는 HTTP 경로는 문서화를 위한 Mock 경로이며
                    실제 메시지는 STOMP Publish Destination으로 전송해야 합니다.
                    """
    )
    @Parameter(
            name = "Authorization",
            description = "Bearer Access Token. 실제로는 STOMP CONNECT Header에 전달합니다.",
            required = true,
            in = ParameterIn.HEADER,
            example = "Bearer eyJhbGciOiJIUzI1NiJ9..."
    )
    @ApiResponse(
            responseCode = "200",
            description = "메시지 전송 응답 예시",
            content = @Content(
                    schema = @Schema(
                            implementation = SendChatMessageResponse.class
                    ),
                    examples = @ExampleObject(
                            value = """
                                    {
                                      "messageType": "TALK",
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