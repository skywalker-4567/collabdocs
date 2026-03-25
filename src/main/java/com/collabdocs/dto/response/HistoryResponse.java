package com.collabdocs.dto.response;

import com.collabdocs.enums.OperationType;

import java.time.LocalDateTime;
import java.util.List;

public class HistoryResponse {

    public record OperationEntry(
            Long operationId,
            OperationType type,
            Integer position,
            String content,
            Integer length,
            Long serverVersion,
            AuthResponse.UserSummary editor,
            LocalDateTime timestamp
    ) {}

    public record FullHistory(
            Long documentId,
            String title,
            Long currentVersion,
            List<OperationEntry> operations
    ) {}
}
