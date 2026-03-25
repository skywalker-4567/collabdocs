package com.collabdocs.dto.response;

import com.collabdocs.enums.Role;

import java.time.LocalDateTime;
import java.util.List;

public class DocumentResponse {

    public record Summary(
            Long id,
            String title,
            Long version,
            String ownerEmail,
            LocalDateTime updatedAt
    ) {}

    public record Detail(
            Long id,
            String title,
            String content,
            Long version,
            String ownerEmail,
            Role yourRole,
            List<PermissionEntry> permissions,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {}

    public record PermissionEntry(
            Long userId,
            String email,
            String fullName,
            Role role,
            LocalDateTime grantedAt
    ) {}
}
