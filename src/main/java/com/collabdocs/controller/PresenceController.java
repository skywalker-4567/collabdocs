package com.collabdocs.controller;

import com.collabdocs.dto.response.PresenceResponse;
import com.collabdocs.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents/{documentId}/presence")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;

    /**
     * POST /api/documents/{documentId}/presence/heartbeat
     *
     * Client calls this every 3 seconds while the document is open.
     * Sets/refreshes a Redis TTL key for the current user on this document.
     * Key expires in 5s — if heartbeat stops, user is considered offline.
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable Long documentId) {
        presenceService.heartbeat(documentId);
        return ResponseEntity.ok().build();
    }

    /**
     * DELETE /api/documents/{documentId}/presence
     *
     * Client calls this on clean disconnect (tab close, navigation away).
     * Best-effort — the TTL will handle it anyway if this isn't called.
     */
    @DeleteMapping
    public ResponseEntity<Void> leave(@PathVariable Long documentId) {
        presenceService.leave(documentId);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/documents/{documentId}/presence
     *
     * Returns list of users currently active on this document.
     * Any user with a valid permission can call this.
     */
    @GetMapping
    public ResponseEntity<PresenceResponse.DocumentPresence> getActiveUsers(
            @PathVariable Long documentId) {
        return ResponseEntity.ok(presenceService.getActiveUsers(documentId));
    }
}