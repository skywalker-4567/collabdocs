package com.collabdocs.controller;

import com.collabdocs.dto.response.HistoryResponse;
import com.collabdocs.service.HistoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/documents/{documentId}/history")
@RequiredArgsConstructor
public class HistoryController {

    private final HistoryService historyService;

    /**
     * GET /api/documents/{documentId}/history
     * Returns the full ordered list of operations on this document.
     * Any role (VIEWER, EDITOR, OWNER) can call this.
     */
    @GetMapping
    public ResponseEntity<HistoryResponse.FullHistory> getHistory(
            @PathVariable Long documentId) {
        return ResponseEntity.ok(historyService.getHistory(documentId));
    }

    /**
     * GET /api/documents/{documentId}/history/preview?version=5
     * Returns what the document content looked like at that version.
     * Does NOT modify anything. Any role can call this.
     */
    @GetMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(
            @PathVariable Long documentId,
            @RequestParam Long version) {
        String content = historyService.previewAtVersion(documentId, version);
        return ResponseEntity.ok(Map.of(
                "documentId", documentId,
                "version", version,
                "content", content
        ));
    }

    /**
     * POST /api/documents/{documentId}/history/restore?version=5
     * Replays all operations up to version 5, saves that as current content.
     * Records the restore as a new operation in the log.
     * OWNER only.
     */
    @PostMapping("/restore")
    public ResponseEntity<HistoryResponse.FullHistory> restore(
            @PathVariable Long documentId,
            @RequestParam Long version) {
        return ResponseEntity.ok(historyService.restoreToVersion(documentId, version));
    }
}