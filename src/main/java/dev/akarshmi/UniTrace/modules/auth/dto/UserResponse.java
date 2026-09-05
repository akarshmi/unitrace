package dev.akarshmi.UniTrace.modules.auth.dto;

public record UserResponse(
        Long registrationNumber,
        String uniEmail,
        String role
) {
}
