package com.collabdocs.dto.request;

import com.collabdocs.enums.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class DocumentRequest {

    public record Create(
            @NotBlank String title,
            String content          // optional — defaults to empty string
    ) {}

    public record Update(
            String title,
            String content
    ) {}

    public record Share(
            @NotBlank @Email String email,
            @NotNull Role role
    ) {}
}
