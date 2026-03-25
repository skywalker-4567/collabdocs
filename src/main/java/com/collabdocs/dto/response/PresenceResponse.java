package com.collabdocs.dto.response;

import java.util.List;

public class PresenceResponse {

    public record ActiveUser(
            Long userId,
            String email,
            String fullName
    ) {}

    public record DocumentPresence(
            Long documentId,
            List<ActiveUser> activeUsers
    ) {}
}
