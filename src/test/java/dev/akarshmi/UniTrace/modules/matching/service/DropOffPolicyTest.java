package dev.akarshmi.UniTrace.modules.matching.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import dev.akarshmi.UniTrace.modules.configs.DeskProperties;

class DropOffPolicyTest {

    private final DropOffPolicy policy =
            new DropOffPolicy(new DeskProperties("s", Duration.ofDays(7), 5, null, "Asia/Kolkata", "17:00"));

    // Asia/Kolkata is UTC+05:30, so 17:00 IST == 11:30Z
    @Test
    void reportedBeforeCutoffIsDueSameDay() {
        assertEquals(Instant.parse("2026-10-30T11:30:00Z"), policy.deadlineFor(Instant.parse("2026-10-30T04:30:00Z")));
    }

    @Test
    void reportedAfterCutoffIsDueNextDay() {
        assertEquals(Instant.parse("2026-10-31T11:30:00Z"), policy.deadlineFor(Instant.parse("2026-10-30T13:00:00Z")));
    }

    @Test
    void reportedExactlyAtCutoffIsDueNextDay() {
        assertEquals(Instant.parse("2026-10-31T11:30:00Z"), policy.deadlineFor(Instant.parse("2026-10-30T11:30:00Z")));
    }

    @Test
    void nullIsNull() {
        assertNull(policy.deadlineFor(null));
    }
}
