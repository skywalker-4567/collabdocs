package com.collabdocs.websocket;

import com.collabdocs.dto.response.PresenceResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Only responsibility: broadcast a presence payload over STOMP.
 * No service dependencies — receives the already-built payload from PresenceService.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PresenceWebSocketHandler {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastPresence(Long documentId, PresenceResponse.DocumentPresence presence) {
        try {
            messagingTemplate.convertAndSend(
                    "/topic/document/" + documentId + "/presence",
                    presence
            );
            log.debug("Broadcasted presence for document {}: {} active users",
                    documentId, presence.activeUsers().size());
        } catch (Exception e) {
            log.error("Failed to broadcast presence for document {}: {}", documentId, e.getMessage());
        }
    }
}