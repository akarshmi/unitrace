package dev.akarshmi.UniTrace.modules.auth.dto;

public record RegistrationVerifyResponse(
        String uniEmail,
        String message
) {}