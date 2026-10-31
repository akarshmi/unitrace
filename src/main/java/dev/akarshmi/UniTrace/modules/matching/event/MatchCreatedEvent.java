package dev.akarshmi.UniTrace.modules.matching.event;

import java.util.UUID;

/**
 * Published inside the matching transaction for every NEW match. Listen with
 * {@code @TransactionalEventListener(phase = AFTER_COMMIT)} to notify the desk / send e-mail.
 */
public record MatchCreatedEvent(UUID matchId, UUID lostItemId, UUID foundItemId, double finalScore) {
}
