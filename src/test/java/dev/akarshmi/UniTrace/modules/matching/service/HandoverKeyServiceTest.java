package dev.akarshmi.UniTrace.modules.matching.service;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.akarshmi.UniTrace.modules.configs.DeskProperties;

class HandoverKeyServiceTest {

    private static DeskProperties props(String secret) {
        return new DeskProperties(secret, Duration.ofDays(7), 5, null, "Asia/Kolkata", "17:00");
    }

    private final HandoverKeyService service = new HandoverKeyService(props("secret-one"));

    @Test
    void generatedKeysHaveExpectedShapeAndAreUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 500; i++) {
            String key = service.generate();
            assertTrue(key.matches("[A-HJ-NP-Z2-9]{4}-[A-HJ-NP-Z2-9]{4}"), key);
            seen.add(key);
        }
        assertTrue(seen.size() > 495);
    }

    @Test
    void hashIsDeterministicAndFormattingInsensitive() {
        assertEquals(service.hash("K7QM-X2PD"), service.hash("k7qm x2pd"));
        assertEquals(64, service.hash("K7QM-X2PD").length());
        assertNotEquals(service.hash("K7QM-X2PD"), service.hash("K7QM-X2PE"));
    }

    @Test
    void matchesAcceptsRightKeyOnly() {
        String key = service.generate();
        String stored = service.hash(key);
        assertTrue(service.matches(key.toLowerCase(), stored));
        assertFalse(service.matches("AAAA-AAAA", stored));
        assertFalse(service.matches(null, stored));
        assertFalse(service.matches(key, null));
    }

    @Test
    void differentSecretProducesDifferentHash() {
        assertNotEquals(service.hash("K7QM-X2PD"), new HandoverKeyService(props("secret-two")).hash("K7QM-X2PD"));
    }

    @Test
    void blankSecretIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> props(" "));
    }
}
