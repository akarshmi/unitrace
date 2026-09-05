package dev.akarshmi.UniTrace.modules.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.akarshmi.UniTrace.modules.auth.dto.PendingRegistration;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.PendingRegistrationExpiredException;
import dev.akarshmi.UniTrace.modules.utils.services.EmailHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class PendingRegistrationService {

    private static final String KEY_PREFIX = "unitrace:pending:";
    private static final String REGNO_PREFIX = "unitrace:reg:reserved:regno:";

    private final StringRedisTemplate redisTemplate;
    private final EmailHashUtil emailHashUtil;
    private final ObjectMapper objectMapper;
    private final SecureRandom random = new SecureRandom();

    @Value("${unitrace.otp.ttl-minutes}")
    private long ttlMinutes;

    public String save(PendingRegistration base) {
        String token = generateToken();
        PendingRegistration withToken = PendingRegistration.builder()
                .token(token)
                .firstName(base.firstName())
                .lastName(base.lastName())
                .personalEmail(base.personalEmail())
                .registrationNumber(base.registrationNumber())
                .uniEmail(base.uniEmail())
                .phoneNumber(base.phoneNumber())
                .hashedPassword(base.hashedPassword())
                .department(base.department())
                .build();

        String key = KEY_PREFIX + emailHashUtil.hash(base.uniEmail());
        String json = writeJson(withToken);
        redisTemplate.opsForValue().set(key, json, Duration.ofMinutes(ttlMinutes));
        return token;
    }

    public PendingRegistration get(String uniEmail) {
        String key = KEY_PREFIX + emailHashUtil.hash(uniEmail);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) {
            throw new PendingRegistrationExpiredException(
                    "Registration session expired. Please register again.");
        }
        return readJson(json);
    }

    public void delete(String uniEmail) {
        redisTemplate.delete(KEY_PREFIX + emailHashUtil.hash(uniEmail));
    }

    // ---------- regNo reservation (blocks concurrent dupes pre-verification) ----------

    public boolean reserveRegistrationNumber(Long registrationNumber, String uniEmail) {
        String key = REGNO_PREFIX + registrationNumber;
        Boolean reserved = redisTemplate.opsForValue()
                .setIfAbsent(key, uniEmail, Duration.ofMinutes(ttlMinutes));

        if (Boolean.TRUE.equals(reserved)) return true;

        // allow the same email to "re-reserve" on a resend/retry
        String reservedBy = redisTemplate.opsForValue().get(key);
        return uniEmail.equals(reservedBy);
    }

    public void releaseRegistrationNumber(Long registrationNumber) {
        redisTemplate.delete(REGNO_PREFIX + registrationNumber);
    }

    // ---------- helpers ----------

    private String generateToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String writeJson(PendingRegistration p) {
        try {
            return objectMapper.writeValueAsString(p);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize pending registration.", e);
        }
    }

    private PendingRegistration readJson(String json) {
        try {
            return objectMapper.readValue(json, PendingRegistration.class);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to deserialize pending registration.", e);
        }
    }
}