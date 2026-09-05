package dev.akarshmi.UniTrace.modules.auth.dto;

public record VerifyRegistrationRequest(
        String uniEmail,
        String token,
        String otp
) {
}
