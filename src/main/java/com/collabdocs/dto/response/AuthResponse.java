package com.collabdocs.dto.response;

public class AuthResponse {

    public record TokenResponse(
            String token,
            String email,
            String fullName
    ) {}

    public record UserSummary(
            Long id,
            String email,
            String fullName
    ) {}
}
