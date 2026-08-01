package com.planb.domain.chat.dto.response;

import com.planb.domain.chat.dto.MessageType;

import java.time.Instant;


public record SendChatMessageResponse(MessageType type,
                                      Long roomId,
                                      Long senderId,
                                      String senderNickname,
                                      String message,
                                      Instant sendTime) {


}
