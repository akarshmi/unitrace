package dev.akarshmi.UniTrace.modules.matching.utill;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Deterministic time score in [0,1] between a LOST event window and a FOUND event window.
 * <ul>
 *   <li>Overlapping windows: 1.0.</li>
 *   <li>Found after the loss window: exponential decay, 0.5 per half-life, 0 at/after maxGap.</li>
 *   <li>Found before the loss began: allowed only within a small tolerance (clock/vagueness), otherwise 0
 *       (an item cannot be found before it was lost).</li>
 *   <li>Missing data: neutral 0.5. A half-specified window (only from or only to) is a single instant.</li>
 *   <li>Future timestamps are clamped to now; a window with from &gt; to is swapped.</li>
 * </ul>
 */
public final class TimeScoringUtils {

    public static final double NEUTRAL_SCORE = 0.5;
    public static final Duration DEFAULT_HALF_LIFE = Duration.ofHours(48);
    public static final Duration DEFAULT_MAX_GAP = Duration.ofDays(14);
    public static final Duration DEFAULT_TOLERANCE = Duration.ofHours(2);

    private TimeScoringUtils() {
    }

    public static double timeScore(Instant lostFrom, Instant lostTo, Instant foundFrom, Instant foundTo, Instant now) {
        return timeScore(lostFrom, lostTo, foundFrom, foundTo, now,
                DEFAULT_HALF_LIFE, DEFAULT_MAX_GAP, DEFAULT_TOLERANCE);
    }

    public static double timeScore(Instant lostFrom, Instant lostTo, Instant foundFrom, Instant foundTo, Instant now,
                                   Duration halfLife, Duration maxGap, Duration foundBeforeLostTolerance) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(halfLife, "halfLife");
        Objects.requireNonNull(maxGap, "maxGap");
        Objects.requireNonNull(foundBeforeLostTolerance, "foundBeforeLostTolerance");
        if (halfLife.isZero() || halfLife.isNegative()) {
            throw new IllegalArgumentException("halfLife must be positive");
        }

        Instant[] lost = window(lostFrom, lostTo, now);
        Instant[] found = window(foundFrom, foundTo, now);
        if (lost == null || found == null) {
            return NEUTRAL_SCORE;
        }

        Duration gap;
        if (found[1].isBefore(lost[0])) {            // found entirely before the loss began
            gap = Duration.between(found[1], lost[0]);
            if (gap.compareTo(foundBeforeLostTolerance) > 0) {
                return 0.0;
            }
        } else if (found[0].isAfter(lost[1])) {      // found after the loss window ended
            gap = Duration.between(lost[1], found[0]);
        } else {
            return 1.0;                              // windows overlap
        }

        if (gap.compareTo(maxGap) >= 0) {
            return 0.0;
        }
        double halfLives = (double) gap.toMillis() / (double) halfLife.toMillis();
        double score = Math.pow(0.5, halfLives);
        return Double.isNaN(score) ? 0.0 : Math.max(0.0, Math.min(1.0, score));
    }

    private static Instant[] window(Instant from, Instant to, Instant now) {
        if (from == null && to == null) {
            return null;
        }
        Instant start = from != null ? from : to;
        Instant end = to != null ? to : from;
        if (start.isAfter(now)) {
            start = now;
        }
        if (end.isAfter(now)) {
            end = now;
        }
        if (start.isAfter(end)) {
            Instant tmp = start;
            start = end;
            end = tmp;
        }
        return new Instant[]{start, end};
    }
}
