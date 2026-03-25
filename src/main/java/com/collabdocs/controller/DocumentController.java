package com.collabdocs.controller;

import com.collabdocs.dto.request.DocumentRequest;
import com.collabdocs.dto.response.DocumentResponse;
import com.collabdocs.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;

    // --- CRUD ---

    @PostMapping
    public ResponseEntity<DocumentResponse.Detail> create(
            @Valid @RequestBody DocumentRequest.Create request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(documentService.createDocument(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse.Detail> get(@PathVariable Long id) {
        return ResponseEntity.ok(documentService.getDocument(id));
    }

    /**
     * Returns all documents the current user has any access to (own + shared).
     * Supports pagination: ?page=0&size=20&sort=updatedAt,desc
     */
    @GetMapping
    public ResponseEntity<Page<DocumentResponse.Summary>> getAll(
            @PageableDefault(size = 20, sort = "updatedAt") Pageable pageable) {
        return ResponseEntity.ok(documentService.getMyDocuments(pageable));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DocumentResponse.Detail> update(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequest.Update request) {
        return ResponseEntity.ok(documentService.updateDocument(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        documentService.deleteDocument(id);
        return ResponseEntity.noContent().build();
    }

    // --- Permissions ---

    /**
     * Share a document with another user.
     * Only the OWNER can call this.
     * Sends an email notification to the target user.
     */
    @PostMapping("/{id}/share")
    public ResponseEntity<DocumentResponse.Detail> share(
            @PathVariable Long id,
            @Valid @RequestBody DocumentRequest.Share request) {
        return ResponseEntity.ok(documentService.shareDocument(id, request));
    }

    /**
     * Revoke a user's access to a document.
     * Only the OWNER can call this.
     * Cannot revoke the owner's own access.
     */
    @DeleteMapping("/{id}/permissions/{userId}")
    public ResponseEntity<Void> revoke(
            @PathVariable Long id,
            @PathVariable Long userId) {
        documentService.revokeAccess(id, userId);
        return ResponseEntity.noContent().build();
    }
}
