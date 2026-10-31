package dev.akarshmi.UniTrace.modules.matching.dto;

import java.time.Instant;
import java.util.UUID;

import dev.akarshmi.UniTrace.modules.matching.entity.MatchStatus;

public record DeskMatchResponse(
        UUID id,
        MatchStatus status,
        double finalScore,
        double textScore,
        boolean imageCompared,
        double imageScore,
        double timeScore,
        DeskItemView lostItem,
        DeskItemView foundItem,
        Instant createdAt,
        Instant reviewedAt,
        String reviewNote,
        Instant handoverKeyExpiresAt,
        int handoverFailedAttempts,
        Instant completedAt) {
}
