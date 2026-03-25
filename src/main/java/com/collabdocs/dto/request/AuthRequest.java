package com.collabdocs.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthRequest {

    public record Register(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 6) String password,
            @NotBlank String fullName
    ) {}

    public record Login(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}
}
