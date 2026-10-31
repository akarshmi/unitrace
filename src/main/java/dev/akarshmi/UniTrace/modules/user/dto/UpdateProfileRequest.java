package dev.akarshmi.UniTrace.modules.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** PATCH semantics: null = leave unchanged. */
public record UpdateProfileRequest(
        @Size(min = 1, max = 100) String firstName,
        @Size(max = 100) String lastName,
        @Email @Size(max = 150) String personalEmail,
        @Pattern(regexp = "^[+0-9 \\-]{7,20}$", message = "Invalid phone number") String phoneNumber,
        @Size(max = 100) String department) {
}
