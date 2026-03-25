package com.collabdocs.websocket;

import com.collabdocs.dto.response.EditResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Listens on Redis Pub/Sub channels and forwards messages to the local
 * STOMP broker. This is what enables horizontal scaling:
 *
 *   Instance A accepts edit → publishes to Redis → all instances receive it
 *   → each instance broadcasts to its locally connected WebSocket clients
 *
 * Registered to specific document channels in RedisSubscriptionService (Phase 3b).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RedisMessageSubscriber implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String body = new String(message.getBody());

        try {
            EditResponse.Accepted accepted = objectMapper.readValue(body, EditResponse.Accepted.class);

            // Extract document ID from channel name: "doc:edits:{id}"
            String[] parts = channel.split(":");
            String documentId = parts[parts.length - 1];

            messagingTemplate.convertAndSend(
                    "/topic/document/" + documentId + "/edits",
                    accepted
            );

            log.debug("Forwarded Redis message from channel {} to STOMP topic", channel);

        } catch (Exception e) {
            log.error("Failed to process Redis message from channel {}: {}", channel, e.getMessage());
        }
    }
}
