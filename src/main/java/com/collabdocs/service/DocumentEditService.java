package com.collabdocs.service;

import com.collabdocs.dto.request.EditRequest;
import com.collabdocs.dto.response.EditResponse;
import com.collabdocs.entity.Document;
import com.collabdocs.entity.DocumentOperation;
import com.collabdocs.entity.User;
import com.collabdocs.enums.Role;
import com.collabdocs.exception.AccessDeniedException;
import com.collabdocs.exception.ResourceNotFoundException;
import com.collabdocs.repository.DocumentOperationRepository;
import com.collabdocs.repository.DocumentRepository;
import com.collabdocs.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentEditService {

    private final DocumentRepository documentRepository;
    private final DocumentOperationRepository operationRepository;
    private final UserRepository userRepository;
    private final DocumentService documentService;
    private final RedisTemplate<String, String> redisTemplate;
    private final RedisSubscriptionService subscriptionService;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    // Redis Pub/Sub channel pattern: "doc:edits:{documentId}"
    private static final String CHANNEL_PREFIX = "doc:edits:";

    // -------------------------------------------------------------------------
    // Process an incoming edit from a connected client
    // -------------------------------------------------------------------------

    @Transactional
    public EditResponse.Accepted processEdit(Long documentId, EditRequest request, String editorEmail) {
        User editor = userRepository.findByEmail(editorEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + editorEmail));

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found: " + documentId));

        // Permission check — must be EDITOR or OWNER
        Role role = documentService.resolveRoleOrThrow(document, editor);
        if (role == Role.VIEWER) {
            throw new AccessDeniedException("VIEWERs cannot edit documents");
        }

        // Version vector check — reject stale edits
        if (!request.clientVersion().equals(document.getVersion())) {
            // Caller handles this by sending a Rejected frame back to the user
            throw new StaleVersionException(request.clientVersion(), document.getVersion());
        }

        // Apply the operation to document content
        String updatedContent = applyOperation(document.getContent(), request);
        document.setContent(updatedContent);

        // Increment server version — @Version handles optimistic locking at DB level
        long newVersion = document.getVersion() + 1;
        document.setVersion(newVersion);
        documentRepository.save(document);

        // Persist operation to history log
        DocumentOperation operation = DocumentOperation.builder()
                .document(document)
                .user(editor)
                .type(request.type())
                .position(request.position())
                .content(request.content())
                .length(request.length())
                .clientVersion(request.clientVersion())
                .serverVersion(newVersion)
                .build();

        operationRepository.save(operation);

        EditResponse.Accepted accepted = new EditResponse.Accepted(
                documentId,
                operation.getId(),
                request.type(),
                request.position(),
                request.content(),
                request.length(),
                newVersion,
                editor.getId(),
                editor.getEmail(),
                LocalDateTime.now()
        );

        // Publish to Redis so other server instances can broadcast to their
        // locally connected clients on the same document
        subscriptionService.subscribeToDocument(documentId);
        publishToRedis(documentId, accepted);

        return accepted;
    }

    // -------------------------------------------------------------------------
    // Apply operation to content string
    // -------------------------------------------------------------------------

    private String applyOperation(String content, EditRequest request) {
        if (content == null) content = "";

        int pos = Math.min(request.position(), content.length());

        return switch (request.type()) {
            case INSERT -> {
                String text = request.content() != null ? request.content() : "";
                yield content.substring(0, pos) + text + content.substring(pos);
            }
            case DELETE -> {
                int end = Math.min(pos + request.length(), content.length());
                yield content.substring(0, pos) + content.substring(end);
            }
            case REPLACE -> {
                int end = Math.min(pos + request.length(), content.length());
                String text = request.content() != null ? request.content() : "";
                yield content.substring(0, pos) + text + content.substring(end);
            }
        };
    }

    // -------------------------------------------------------------------------
    // Redis Pub/Sub
    // -------------------------------------------------------------------------

    private void publishToRedis(Long documentId, EditResponse.Accepted payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            String channel = CHANNEL_PREFIX + documentId;
            redisTemplate.convertAndSend(channel, json);
            log.debug("Published edit to Redis channel {}", channel);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize edit for Redis publish: {}", e.getMessage());
        }
    }

    public static String channelFor(Long documentId) {
        return CHANNEL_PREFIX + documentId;
    }

    // -------------------------------------------------------------------------
    // Inner exception — caught in the WebSocket controller to send Rejected frame
    // -------------------------------------------------------------------------

    public static class StaleVersionException extends RuntimeException {
        private final Long clientVersion;
        private final Long serverVersion;

        public StaleVersionException(Long clientVersion, Long serverVersion) {
            super("Stale version: client=" + clientVersion + ", server=" + serverVersion);
            this.clientVersion = clientVersion;
            this.serverVersion = serverVersion;
        }

        public Long getClientVersion() { return clientVersion; }
        public Long getServerVersion() { return serverVersion; }
    }
}
