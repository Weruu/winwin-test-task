package com.winwin.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class Dtos {
    private Dtos() {}

    public record RegisterRequest(@NotBlank @Email String email,
                                  @NotBlank @Size(min = 4, max = 72) String password) {}

    public record RegisterResponse(UUID id, String email) {}

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {}

    public record TokenResponse(String token) {}

    public record ProcessRequest(@NotBlank @Size(max = 10000) String text) {}

    public record ProcessResponse(String result) {}

    // обмен с data-api
    public record TransformRequest(String text) {}

    public record TransformResponse(String result) {}
}