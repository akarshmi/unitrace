package dev.akarshmi.UniTrace.modules.matching.service;

import java.util.List;
import java.util.UUID;

public interface MatchingService {

    /**
     * Finds opposite-type candidates for the item, scores them and persists new matches (idempotent: existing pairs
     * are skipped). Returns only the matches created by this call. Safe to call repeatedly.
     */
    List<ScoredMatch> findAndRecordMatches(UUID itemId);

    record ScoredMatch(UUID matchId,
                       UUID lostItemId,
                       UUID foundItemId,
                       double textScore,
                       boolean imageCompared,
                       double imageScore,
                       double timeScore,
                       double finalScore) {

        public ScoredMatch withMatchId(UUID id) {
            return new ScoredMatch(id, lostItemId, foundItemId, textScore, imageCompared, imageScore, timeScore, finalScore);
        }
    }
}
