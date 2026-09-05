package dev.akarshmi.UniTrace.modules.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Email @NotBlank
        String uniEmail,
        @NotBlank
        String password
) {
}
