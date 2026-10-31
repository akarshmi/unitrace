package dev.akarshmi.UniTrace.modules.matching.utill;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class TimeScoringUtilsTest {

    private static final Instant NOW = Instant.parse("2026-10-30T12:00:00Z");

    private static Instant ago(long hours) {
        return NOW.minus(Duration.ofHours(hours));
    }

    @Test
    void overlappingWindowsScoreOne() {
        assertEquals(1.0, TimeScoringUtils.timeScore(ago(10), ago(5), ago(7), ago(6), NOW));
        assertEquals(1.0, TimeScoringUtils.timeScore(ago(10), ago(5), ago(5), ago(2), NOW)); // touching
    }

    @Test
    void decaysByHalfLifeWhenFoundAfterLoss() {
        // lost ended 100h ago, found 52h ago => gap 48h == one half-life
        double s = TimeScoringUtils.timeScore(ago(110), ago(100), ago(52), ago(52), NOW);
        assertEquals(0.5, s, 1e-9);
    }

    @Test
    void foundBeforeLostBeyondToleranceIsImpossible() {
        assertEquals(0.0, TimeScoringUtils.timeScore(ago(5), ago(4), ago(20), ago(19), NOW));
    }

    @Test
    void foundSlightlyBeforeLostIsTolerated() {
        double s = TimeScoringUtils.timeScore(ago(5), ago(4), ago(6), ago(6), NOW); // 1h before, tolerance 2h
        assertTrue(s > 0.9 && s < 1.0);
    }

    @Test
    void beyondMaxGapIsZero() {
        assertEquals(0.0, TimeScoringUtils.timeScore(ago(24 * 30), ago(24 * 30), ago(1), ago(1), NOW));
    }

    @Test
    void missingDataIsNeutral() {
        assertEquals(TimeScoringUtils.NEUTRAL_SCORE, TimeScoringUtils.timeScore(null, null, ago(2), ago(1), NOW));
        assertEquals(TimeScoringUtils.NEUTRAL_SCORE, TimeScoringUtils.timeScore(ago(2), ago(1), null, null, NOW));
        assertEquals(TimeScoringUtils.NEUTRAL_SCORE, TimeScoringUtils.timeScore(null, null, null, null, NOW));
    }

    @Test
    void halfSpecifiedWindowIsAnInstant() {
        assertEquals(1.0, TimeScoringUtils.timeScore(ago(5), null, null, ago(5), NOW));
    }

    @Test
    void futureTimestampsAreClampedToNow() {
        Instant future = NOW.plus(Duration.ofDays(3));
        double s = TimeScoringUtils.timeScore(ago(3), ago(2), future, future.plusSeconds(60), NOW);
        assertTrue(s >= 0.0 && s <= 1.0);
        assertFalse(Double.isNaN(s));
        // clamped found = NOW, lost ended 2h ago => small gap, high score
        assertTrue(s > 0.9);
    }

    @Test
    void reversedWindowIsSwapped() {
        assertEquals(1.0, TimeScoringUtils.timeScore(ago(5), ago(10), ago(7), ago(7), NOW));
    }

    @Test
    void invalidHalfLifeRejected() {
        assertThrows(IllegalArgumentException.class, () -> TimeScoringUtils.timeScore(
                ago(2), ago(1), ago(1), ago(1), NOW, Duration.ZERO, Duration.ofDays(1), Duration.ZERO));
        assertThrows(NullPointerException.class, () -> TimeScoringUtils.timeScore(ago(2), ago(1), ago(1), ago(1), null));
    }
}
