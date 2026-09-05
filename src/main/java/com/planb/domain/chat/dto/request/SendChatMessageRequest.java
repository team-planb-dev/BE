package com.planb.domain.chat.dto.request;

import com.planb.domain.chat.dto.MessageType;

public record SendChatMessageRequest(MessageType type, String message) {
}
