package dev.akarshmi.UniTrace.modules.auth.dto;

import java.util.UUID;

public record UserSummary(
        UUID id,
        String uniEmail,
        String role
) {
}
