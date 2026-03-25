package com.collabdocs.dto.request;

import com.collabdocs.enums.OperationType;
import jakarta.validation.constraints.NotNull;

/**
 * Payload sent by clients over STOMP to /app/document/{id}/edit
 *
 * Fields:
 *  - type:          INSERT | DELETE | REPLACE
 *  - position:      character index where the operation starts
 *  - content:       text to insert/replace (null for DELETE)
 *  - length:        number of characters to delete/replace (0 for INSERT)
 *  - clientVersion: the server version the client currently holds
 */
public record EditRequest(
        @NotNull OperationType type,
        @NotNull Integer position,
        String content,
        @NotNull Integer length,
        @NotNull Long clientVersion
) {}
