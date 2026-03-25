package com.collabdocs.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class CommentRequest {

    public record CreateThread(
            @NotNull Integer startIndex,
            @NotNull Integer endIndex,
            @NotBlank String body       // first comment body
    ) {}

    public record AddComment(
            @NotBlank String body
    ) {}

    public record UpdateComment(
            @NotBlank String body
    ) {}
}
