package dev.akarshmi.UniTrace.modules.matching.event;

import java.time.Instant;
import java.util.UUID;

/** Published when a moderator approves a match (or re-issues the key). Carries the PLAIN key for the e-mail only. */
public record MatchApprovedEvent(
        UUID matchId,
        String lostOwnerEmail,
        String foundOwnerEmail,
        String lostTitle,
        String foundTitle,
        String handoverKey,
        Instant keyExpiresAt,
        boolean reissued) {

    @Override
    public String toString() { // never leak the key into logs
        return "MatchApprovedEvent[matchId=" + matchId + ", reissued=" + reissued + "]";
    }
}
