package com.collabdocs.dto.response;

import com.collabdocs.enums.OperationType;

import java.time.LocalDateTime;

public class EditResponse {

    /**
     * Broadcast to /topic/document/{id}/edits when an edit is accepted.
     */
    public record Accepted(
            Long documentId,
            Long operationId,
            OperationType type,
            Integer position,
            String content,
            Integer length,
            Long serverVersion,
            Long editorId,
            String editorEmail,
            LocalDateTime timestamp
    ) {}

    /**
     * Sent back to the client only when their edit is rejected (stale version).
     * The client should re-sync using the current serverVersion and content.
     */
    public record Rejected(
            Long documentId,
            Long clientVersion,
            Long serverVersion,
            String reason
    ) {}
}
