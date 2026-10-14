package com.planb.global.websocket.interceptor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ExecutorChannelInterceptor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * 클라이언트 전송 프레임의 실제 전송 스레드·크기 기록
 * 메시지 본문 내용 제외, 길이만 기록
 */
@Component
@Slf4j
public class StompOutboundChannelInterceptor implements ExecutorChannelInterceptor {

    // Java 클라이언트 기본 텍스트 버퍼 8192자에서 STOMP 헤더 여유분을 뺀 대형 본문 기준
    private static final int LARGE_PAYLOAD_BYTES =
            8192 - 512;

    @Override
    public Message<?> beforeHandle(
            Message<?> message,
            MessageChannel channel,
            MessageHandler handler
    ) {

        MessageHeaders headers = message.getHeaders();

        if (SimpMessageHeaderAccessor.getMessageType(headers) != SimpMessageType.MESSAGE
                || !(message.getPayload() instanceof byte[] payload)) {
            return message;
        }

        // 대형 본문만 문자 수 계산과 INFO 기록, 나머지는 DEBUG 바이트 기록
        if (payload.length <= LARGE_PAYLOAD_BYTES) {
            log.debug(
                    "STOMP OUTBOUND - sessionId: {}, destination: {}, payloadBytes: {}",
                    SimpMessageHeaderAccessor.getSessionId(headers),
                    SimpMessageHeaderAccessor.getDestination(headers),
                    payload.length
            );

            return message;
        }

        log.info(
                "STOMP OUTBOUND LARGE - sessionId: {}, destination: {}, payloadBytes: {}, payloadChars: {}, "
                        + "senderThread: {}, virtual: {}",
                SimpMessageHeaderAccessor.getSessionId(headers),
                SimpMessageHeaderAccessor.getDestination(headers),
                payload.length,
                new String(
                        payload,
                        StandardCharsets.UTF_8
                )
                        .length(),
                Thread
                        .currentThread()
                        .getName(),
                Thread
                        .currentThread()
                        .isVirtual()
        );

        return message;
    }
}
