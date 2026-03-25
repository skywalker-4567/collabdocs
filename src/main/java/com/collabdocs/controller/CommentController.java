package com.collabdocs.controller;

import com.collabdocs.dto.request.CommentRequest;
import com.collabdocs.dto.response.CommentResponse;
import com.collabdocs.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents/{documentId}/threads")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // --- Threads ---

    @PostMapping
    public ResponseEntity<CommentResponse.ThreadDetail> createThread(
            @PathVariable Long documentId,
            @Valid @RequestBody CommentRequest.CreateThread request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.createThread(documentId, request));
    }

    /**
     * GET /api/documents/{documentId}/threads
     * Optional query param: ?resolved=false (open only) or ?resolved=true (closed only)
     * Omit param to get all threads.
     */
    @GetMapping
    public ResponseEntity<List<CommentResponse.ThreadDetail>> getThreads(
            @PathVariable Long documentId,
            @RequestParam(required = false) Boolean resolved) {
        return ResponseEntity.ok(commentService.getThreads(documentId, resolved));
    }

    @PostMapping("/{threadId}/resolve")
    public ResponseEntity<CommentResponse.ThreadDetail> resolveThread(
            @PathVariable Long documentId,
            @PathVariable Long threadId) {
        return ResponseEntity.ok(commentService.resolveThread(documentId, threadId));
    }

    @DeleteMapping("/{threadId}")
    public ResponseEntity<Void> deleteThread(
            @PathVariable Long documentId,
            @PathVariable Long threadId) {
        commentService.deleteThread(documentId, threadId);
        return ResponseEntity.noContent().build();
    }

    // --- Comments (replies) ---

    @PostMapping("/{threadId}/comments")
    public ResponseEntity<CommentResponse.ThreadDetail> addComment(
            @PathVariable Long documentId,
            @PathVariable Long threadId,
            @Valid @RequestBody CommentRequest.AddComment request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(commentService.addComment(documentId, threadId, request));
    }

    @PutMapping("/{threadId}/comments/{commentId}")
    public ResponseEntity<CommentResponse.CommentDetail> updateComment(
            @PathVariable Long documentId,
            @PathVariable Long threadId,
            @PathVariable Long commentId,
            @Valid @RequestBody CommentRequest.UpdateComment request) {
        return ResponseEntity.ok(
                commentService.updateComment(documentId, threadId, commentId, request));
    }

    @DeleteMapping("/{threadId}/comments/{commentId}")
    public ResponseEntity<Void> deleteComment(
            @PathVariable Long documentId,
            @PathVariable Long threadId,
            @PathVariable Long commentId) {
        commentService.deleteComment(documentId, threadId, commentId);
        return ResponseEntity.noContent().build();
    }
}