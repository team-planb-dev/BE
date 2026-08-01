package com.planb.domain.chat.websocket.registry;

import org.springframework.stereotype.Component;
import com.planb.domain.chat.websocket.ChatSubscriptionInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatSubscriptionRegistry {

    private final ConcurrentHashMap<String,ConcurrentHashMap<String, ChatSubscriptionInfo>>
    subscriptionsBySession = new ConcurrentHashMap<>();

    public boolean subscribe(String sessionId,
                             String subscriptionId,
                             Long roomId,
                             String username){

        ConcurrentHashMap<String,ChatSubscriptionInfo> sessionSubscriptions =
                subscriptionsBySession.computeIfAbsent(
                        sessionId,
                        ignored -> new ConcurrentHashMap<>()
                );

        ChatSubscriptionInfo previous = sessionSubscriptions.putIfAbsent(
                subscriptionId,
                new ChatSubscriptionInfo(roomId,username)
        );

        return previous == null;
    }

    public ChatSubscriptionInfo unsubscribe(String sessionId,
                                            String subscriptionId){

        ConcurrentHashMap<String,ChatSubscriptionInfo> sessionSubscriptions =
                subscriptionsBySession.get(sessionId);

        if(sessionSubscriptions == null){
            return null;
        }

        ChatSubscriptionInfo removed =
                sessionSubscriptions.remove(subscriptionId);

        if(sessionSubscriptions.isEmpty()){
            subscriptionsBySession.remove(sessionId,sessionSubscriptions);
        }

        return removed;

    }

    public List<ChatSubscriptionInfo> disconnect(String sessionId){

        Map<String,ChatSubscriptionInfo> removed =
                subscriptionsBySession.remove(sessionId);

        if (removed == null){
            return List.of();
        }

        return List.copyOf(removed.values());
    }
}
