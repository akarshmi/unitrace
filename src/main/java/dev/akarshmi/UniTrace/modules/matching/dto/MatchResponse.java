package dev.akarshmi.UniTrace.modules.matching.dto;

import java.time.Instant;
import java.util.UUID;

import dev.akarshmi.UniTrace.modules.matching.entity.MatchStatus;

/** Student-facing view of an APPROVED / COMPLETED match. */
public record MatchResponse(
        UUID id,
        MatchStatus status,
        String yourRole,
        int confidencePercent,
        double finalScore,
        double textScore,
        boolean imageCompared,
        double imageScore,
        double timeScore,
        MatchItemView yourItem,
        MatchItemView otherItem,
        boolean handoverKeyIssued,
        Instant handoverKeyExpiresAt,
        String nextStep,
        Instant createdAt,
        Instant reviewedAt,
        Instant completedAt) {
}
