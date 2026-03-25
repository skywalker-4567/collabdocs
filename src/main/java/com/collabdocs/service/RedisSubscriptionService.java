package com.collabdocs.service;

import com.collabdocs.websocket.RedisMessageSubscriber;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamically registers/unregisters Redis Pub/Sub listeners per document.
 * Called when the first client opens a document (subscribe) and when the
 * last client leaves (unsubscribe) — wired in Phase 4 with presence tracking.
 *
 * For now, subscriptions are registered lazily on first edit to a document.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisSubscriptionService {

    private final RedisMessageListenerContainer listenerContainer;
    private final RedisMessageSubscriber subscriber;

    // Track which document channels we're already subscribed to
    private final Set<String> activeChannels = ConcurrentHashMap.newKeySet();

    public void subscribeToDocument(Long documentId) {
        String channel = DocumentEditService.channelFor(documentId);
        if (activeChannels.add(channel)) {
            listenerContainer.addMessageListener(subscriber, new ChannelTopic(channel));
            log.info("Subscribed to Redis channel: {}", channel);
        }
    }

    public void unsubscribeFromDocument(Long documentId) {
        String channel = DocumentEditService.channelFor(documentId);
        if (activeChannels.remove(channel)) {
            listenerContainer.removeMessageListener(subscriber, new ChannelTopic(channel));
            log.info("Unsubscribed from Redis channel: {}", channel);
        }
    }

    public boolean isSubscribed(Long documentId) {
        return activeChannels.contains(DocumentEditService.channelFor(documentId));
    }
}
