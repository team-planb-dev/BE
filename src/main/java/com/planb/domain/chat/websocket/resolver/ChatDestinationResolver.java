package com.planb.domain.chat.websocket.resolver;

import org.springframework.stereotype.Component;

@Component
public class ChatDestinationResolver {

    private static final String CHAT_SUBSCRIPTION_PREFIX =
            "/sub/api/v1/chat/";

    private static final String CHAT_SEND_PREFIX =
            "/pub/api/v1/chat/";

    private static final String CHAT_SEND_SUFFIX =
            "/send";

    public Long extractRoomId(String destination) {

        return extractRoomId(
                destination,
                CHAT_SUBSCRIPTION_PREFIX,
                ""
        );
    }

    public Long extractSendRoomId(String destination) {

        return extractRoomId(
                destination,
                CHAT_SEND_PREFIX,
                CHAT_SEND_SUFFIX
        );
    }

    private Long extractRoomId(
            String destination,
            String prefix,
            String suffix
    ) {

        if (destination == null
                || !destination.startsWith(prefix)
                || !destination.endsWith(suffix)) {

            return null;
        }

        String roomIdValue = destination.substring(
                prefix.length(),
                destination.length() - suffix.length()
        );

        if (roomIdValue.isBlank()
                || roomIdValue.contains("/")) {

            return null;
        }

        try {
            return Long.parseLong(roomIdValue);
        } catch (NumberFormatException exception) {
            return null;
        }
    }
}
