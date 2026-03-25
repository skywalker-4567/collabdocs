package com.collabdocs.dto.response;

import java.time.LocalDateTime;
import java.util.List;

public class CommentResponse {

    public record ThreadDetail(
            Long id,
            Long documentId,
            Integer startIndex,
            Integer endIndex,
            String anchoredText,
            boolean resolved,
            AuthResponse.UserSummary createdBy,
            List<CommentDetail> comments,
            LocalDateTime createdAt
    ) {}

    public record CommentDetail(
            Long id,
            Long threadId,
            AuthResponse.UserSummary author,
            String body,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}
}
