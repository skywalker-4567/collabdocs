package com.collabdocs.websocket;

import com.collabdocs.dto.request.EditRequest;
import com.collabdocs.dto.response.EditResponse;
import com.collabdocs.entity.Document;
import com.collabdocs.entity.User;
import com.collabdocs.exception.AccessDeniedException;
import com.collabdocs.repository.DocumentRepository;
import com.collabdocs.service.DocumentEditService;
import com.collabdocs.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
@Slf4j
public class DocumentEditController {

    private final DocumentEditService editService;
    private final DocumentService documentService;
    private final DocumentRepository documentRepository;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * Client sends edit to: /app/document/{id}/edit
     *
     * On success  → broadcasts EditResponse.Accepted to /topic/document/{id}/edits
     * On stale    → sends EditResponse.Rejected to /user/queue/errors (sender only)
     * On no access→ sends error to /user/queue/errors
     */
    @MessageMapping("/document/{id}/edit")
    public void handleEdit(
            @DestinationVariable Long id,
            @Payload EditRequest request,
            Principal principal
    ) {
        String editorEmail = principal.getName();

        try {
            EditResponse.Accepted accepted = editService.processEdit(id, request, editorEmail);

            // Broadcast to all subscribers on this document
            messagingTemplate.convertAndSend(
                    "/topic/document/" + id + "/edits",
                    accepted
            );

            log.debug("Edit accepted for document {} at version {}", id, accepted.serverVersion());

        } catch (DocumentEditService.StaleVersionException ex) {
            EditResponse.Rejected rejected = new EditResponse.Rejected(
                    id,
                    ex.getClientVersion(),
                    ex.getServerVersion(),
                    "Stale version — re-sync and retry"
            );
            messagingTemplate.convertAndSendToUser(
                    editorEmail, "/queue/errors", rejected
            );
            log.debug("Edit rejected for document {} — stale version from {}", id, editorEmail);

        } catch (AccessDeniedException ex) {
            messagingTemplate.convertAndSendToUser(
                    editorEmail, "/queue/errors",
                    new EditResponse.Rejected(id, request.clientVersion(), null, ex.getMessage())
            );
        } catch (Exception ex) {
            log.error("Unexpected error processing edit for document {}: {}", id, ex.getMessage(), ex);
            messagingTemplate.convertAndSendToUser(
                    editorEmail, "/queue/errors",
                    new EditResponse.Rejected(id, request.clientVersion(), null, "Internal server error")
            );
        }
    }

    /**
     * Client sends to: /app/document/{id}/sync
     * Returns the full current document state so the client can re-sync
     * after receiving a Rejected frame.
     */
    @MessageMapping("/document/{id}/sync")
    public void handleSync(
            @DestinationVariable Long id,
            Principal principal
    ) {
        String email = principal.getName();

        try {
            User user = (User) ((org.springframework.security.core.Authentication) principal).getPrincipal();
            Document document = documentRepository.findById(id)
                    .orElseThrow(() -> new com.collabdocs.exception.ResourceNotFoundException("Document not found: " + id));

            documentService.resolveRoleOrThrow(document, user); // access check

            // Send current state directly to the requesting user
            messagingTemplate.convertAndSendToUser(
                    email, "/queue/sync",
                    new SyncResponse(document.getId(), document.getContent(), document.getVersion())
            );

        } catch (Exception ex) {
            log.warn("Sync failed for document {} user {}: {}", id, email, ex.getMessage());
            messagingTemplate.convertAndSendToUser(
                    email, "/queue/errors",
                    new EditResponse.Rejected(id, null, null, ex.getMessage())
            );
        }
    }

    // Lightweight sync payload
    public record SyncResponse(Long documentId, String content, Long version) {}
}
