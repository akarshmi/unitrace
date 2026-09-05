package dev.akarshmi.UniTrace.modules.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank
        String firstName,

        String lastName,

        @Email
        String personalEmail,

        @NotNull
        Long registrationNumber,

        @NotBlank @Email
        String uniEmail,

        @NotBlank
        @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be 10 digits")
        String phoneNumber,

        @NotBlank
        @Size(min = 8, max = 64)
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain upper, lower, and a digit"
        )
        String password,

        String department
) {}
