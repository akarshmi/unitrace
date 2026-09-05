package dev.akarshmi.UniTrace.modules.auth.dto;

import lombok.Builder;

@Builder
public record TokenResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        UserSummary user,
        String timestamp
) {
}
