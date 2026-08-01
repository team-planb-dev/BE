package com.planb.domain.chat.websocket;

public record ResolvedSubscription(String sessionId,
                                   String subscriptionId,
                                   Long roomId,
                                   String username) {
}
