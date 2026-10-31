package dev.akarshmi.UniTrace.modules.matching.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

import dev.akarshmi.UniTrace.modules.configs.DeskProperties;

/** Generates one-time handover keys (shown to the student) and stores only an HMAC of them. */
@Component
public class HandoverKeyService {

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // no 0/O/1/I
    private static final int LENGTH = 8;

    private final SecureRandom random = new SecureRandom();
    private final byte[] secret;

    public HandoverKeyService(DeskProperties props) {
        this.secret = props.handoverSecret().getBytes(StandardCharsets.UTF_8);
    }

    /** e.g. "K7QM-X2PD" */
    public String generate() {
        StringBuilder sb = new StringBuilder(LENGTH + 1);
        for (int i = 0; i < LENGTH; i++) {
            if (i == LENGTH / 2) {
                sb.append('-');
            }
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    public String hash(String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalize(key).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash handover key", e);
        }
    }

    public boolean matches(String key, String storedHash) {
        if (key == null || storedHash == null) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(key).getBytes(StandardCharsets.UTF_8), storedHash.getBytes(StandardCharsets.UTF_8));
    }

    static String normalize(String key) {
        return key == null ? "" : key.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
