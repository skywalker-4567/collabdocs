package com.collabdocs.service;

import com.collabdocs.dto.response.PresenceResponse;
import com.collabdocs.entity.User;
import com.collabdocs.exception.ResourceNotFoundException;
import com.collabdocs.repository.DocumentRepository;
import com.collabdocs.repository.UserRepository;
import com.collabdocs.util.SecurityUtils;
import com.collabdocs.websocket.PresenceWebSocketHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceService {

    private final RedisTemplate<String, String> redisTemplate;
    private final UserRepository userRepository;
    private final DocumentRepository documentRepository;
    private final SecurityUtils securityUtils;
    private final PresenceWebSocketHandler presenceWebSocketHandler;

    @Value("${app.redis.presence.ttl:5}")
    private long presenceTtlSeconds;

    // Key pattern: "presence:{documentId}:{userId}"
    private static final String PRESENCE_PREFIX = "presence:";
    // Scan pattern to find all users on a document: "presence:{documentId}:*"
    private static final String PRESENCE_SCAN_PATTERN = "presence:%d:*";

    // -------------------------------------------------------------------------
    // Heartbeat — client calls this every 3s to stay present
    // -------------------------------------------------------------------------

    public void heartbeat(Long documentId) {
        User currentUser = securityUtils.getCurrentUser();

        // Verify document exists
        if (!documentRepository.existsById(documentId)) {
            throw new ResourceNotFoundException("Document not found: " + documentId);
        }

        String key = buildKey(documentId, currentUser.getId());

        // SET key value EX ttl — refreshes TTL on every heartbeat
        // Value stores enough info to reconstruct the user without a DB hit
        String value = currentUser.getId() + ":" + currentUser.getEmail() + ":" + currentUser.getFullName();
        redisTemplate.opsForValue().set(key, value, presenceTtlSeconds, TimeUnit.SECONDS);
        presenceWebSocketHandler.broadcastPresence(documentId, getActiveUsers(documentId));

        log.debug("Heartbeat: user {} on document {}", currentUser.getEmail(), documentId);
    }

    // -------------------------------------------------------------------------
    // Leave — client calls on disconnect or tab close (best-effort)
    // -------------------------------------------------------------------------

    public void leave(Long documentId) {
        User currentUser = securityUtils.getCurrentUser();
        String key = buildKey(documentId, currentUser.getId());
        redisTemplate.delete(key);
        presenceWebSocketHandler.broadcastPresence(documentId, getActiveUsers(documentId));
        log.debug("Leave: user {} left document {}", currentUser.getEmail(), documentId);
    }

    // -------------------------------------------------------------------------
    // Get active users for a document
    // -------------------------------------------------------------------------

    public PresenceResponse.DocumentPresence getActiveUsers(Long documentId) {
        if (!documentRepository.existsById(documentId)) {
            throw new ResourceNotFoundException("Document not found: " + documentId);
        }

        String pattern = String.format(PRESENCE_SCAN_PATTERN, documentId);
        Set<String> keys = redisTemplate.keys(pattern);

        if (keys == null || keys.isEmpty()) {
            return new PresenceResponse.DocumentPresence(documentId, List.of());
        }

        List<PresenceResponse.ActiveUser> activeUsers = keys.stream()
                .map(key -> redisTemplate.opsForValue().get(key))
                .filter(value -> value != null && !value.isBlank())
                .map(this::parsePresenceValue)
                .toList();

        return new PresenceResponse.DocumentPresence(documentId, activeUsers);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String buildKey(Long documentId, Long userId) {
        return PRESENCE_PREFIX + documentId + ":" + userId;
    }

    /**
     * Value format: "{userId}:{email}:{fullName}"
     * fullName may contain colons so we split with a limit of 3.
     */
    private PresenceResponse.ActiveUser parsePresenceValue(String value) {
        String[] parts = value.split(":", 3);
        return new PresenceResponse.ActiveUser(
                Long.parseLong(parts[0]),
                parts[1],
                parts.length > 2 ? parts[2] : ""
        );
    }
}