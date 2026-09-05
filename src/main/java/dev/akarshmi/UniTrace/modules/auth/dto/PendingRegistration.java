package dev.akarshmi.UniTrace.modules.auth.dto;

import lombok.Builder;

@Builder
public record PendingRegistration(
        String token,
        String firstName,
        String lastName,
        String personalEmail,
        Long registrationNumber,
        String uniEmail,
        String phoneNumber,
        String hashedPassword,
        String department
) {}