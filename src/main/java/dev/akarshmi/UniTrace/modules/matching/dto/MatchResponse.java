package dev.akarshmi.UniTrace.modules.matching.dto;

import java.time.Instant;
import java.util.UUID;

import dev.akarshmi.UniTrace.modules.matching.entity.MatchStatus;

/** Student-facing view of an APPROVED / COMPLETED match. */
public record MatchResponse(
        UUID id,
        MatchStatus status,
        /** LOST_OWNER or FOUND_OWNER: the viewer's side of the match. */
        String yourRole,
        /** Rounded 0-100 confidence from the AI score. */
        int confidencePercent,
        MatchItemView otherItem,
        Instant handoverKeyExpiresAt,
        String nextStep,
        Instant createdAt) {
}
