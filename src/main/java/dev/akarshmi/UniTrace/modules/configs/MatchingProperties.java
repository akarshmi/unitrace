package dev.akarshmi.UniTrace.modules.configs;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Scoring model (all component scores are in [0,1], higher is better):
 * finalScore = textWeight*text + imageWeight*image + timeWeight*time (weights are normalised to sum to 1).
 */
@ConfigurationProperties(prefix = "unitrace.matching")
public record MatchingProperties(
        @DefaultValue("0.5") double textWeight,
        @DefaultValue("0.35") double imageWeight,
        @DefaultValue("0.15") double timeWeight,
        @DefaultValue("0.55") double threshold,
        @DefaultValue("20") int candidateLimit,
        @DefaultValue("10") int maxMatchesPerItem,
        @DefaultValue("0.30") double minTextSimilarity,
        @DefaultValue("0.5") double neutralImageScore,
        @DefaultValue("0.60") double imageSimilarityFloor,
        @DefaultValue("0.95") double imageSimilarityCeiling,
        @DefaultValue("OPEN") List<String> eligibleStatuses,
        @DefaultValue("48h") Duration timeHalfLife,
        @DefaultValue("14d") Duration timeMaxGap,
        @DefaultValue("2h") Duration timeFoundBeforeLostTolerance,
        @DefaultValue("true") boolean rejectImpossibleTime) {

    public MatchingProperties {
        if (textWeight < 0 || imageWeight < 0 || timeWeight < 0) {
            throw new IllegalArgumentException("Matching weights must not be negative");
        }
        double sum = textWeight + imageWeight + timeWeight;
        if (sum <= 0) {
            throw new IllegalArgumentException("At least one matching weight must be positive");
        }
        textWeight /= sum;
        imageWeight /= sum;
        timeWeight /= sum;
        if (threshold < 0 || threshold > 1) {
            throw new IllegalArgumentException("unitrace.matching.threshold must be within [0,1]");
        }
        if (candidateLimit < 1 || maxMatchesPerItem < 1) {
            throw new IllegalArgumentException("candidate-limit and max-matches-per-item must be >= 1");
        }
        if (imageSimilarityCeiling <= imageSimilarityFloor) {
            throw new IllegalArgumentException("image-similarity-ceiling must be greater than the floor");
        }
        if (timeHalfLife.isZero() || timeHalfLife.isNegative()) {
            throw new IllegalArgumentException("time-half-life must be positive");
        }
        eligibleStatuses = eligibleStatuses == null ? List.of() : List.copyOf(eligibleStatuses);
    }
}
