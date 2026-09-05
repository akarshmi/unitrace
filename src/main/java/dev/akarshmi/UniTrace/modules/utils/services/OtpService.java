package dev.akarshmi.UniTrace.modules.utils.services;

import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.OtpAttemptsExceededException;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.OtpCooldownException;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.OtpExpiredException;
import dev.akarshmi.UniTrace.modules.exceptions.customExceptions.OtpLockedException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OtpService {

    private static final String KEY_PREFIX = "unitrace:otp:";
    private static final String COOLDOWN_PREFIX = "unitrace:otp:cooldown:";
    private static final String ATTEMPT_PREFIX = "unitrace:otp:attempt:";
    private static final String LOCK_PREFIX = "unitrace:otp:lock:";
    private static final String UNIVERSITY_EMAIL_PATTERN = "^[^@\\s]+@lpu\\.in$";

    private static final String VERIFY_SCRIPT = """
            local storedHash = redis.call('GET', KEYS[1])

            if not storedHash then
                return -1
            end

            if storedHash == ARGV[1] then
                redis.call('DEL', KEYS[1])
                redis.call('DEL', KEYS[2])
                return 1
            end

            local attempts = redis.call('INCR', KEYS[2])

            if attempts == 1 then
                redis.call('EXPIRE', KEYS[2], ARGV[2])
            end

            if attempts >= tonumber(ARGV[3]) then
                redis.call('SET', KEYS[3], '1', 'EX', ARGV[4])
                redis.call('DEL', KEYS[1])
                redis.call('DEL', KEYS[2])
                return -2
            end

            return 0
            """;

    private static final DefaultRedisScript<Long> VERIFY_SCRIPT_OBJ =
            new DefaultRedisScript<>(VERIFY_SCRIPT, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final EmailHashUtil emailHashUtil;
    private final EmailSender emailSender;
    private final SecureRandom random = new SecureRandom();

    @Value("${unitrace.otp.secret}")
    private String secret;

    @Value("${unitrace.otp.ttl-minutes}")
    private long otpTtlMinutes;

    @Value("${unitrace.otp.cooldown-seconds}")
    private long cooldownSeconds;

    @Value("${unitrace.otp.lockout-minutes}")
    private long lockoutMinutes;

    @Value("${unitrace.otp.max-attempt}")
    private int maxAttempts;

    // ---------- public API ----------

    public void generateOtpAndSendEmail(String email) {
        String normalizedEmail = normalizeEmail(email);
        OtpKeys keys = keysFor(normalizedEmail);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(keys.lockKey()))) {
            throw new OtpLockedException();
        }

        boolean cooldownAcquired = Boolean.TRUE.equals(
                redisTemplate.opsForValue().setIfAbsent(
                        keys.cooldownKey(), "1", Duration.ofSeconds(cooldownSeconds)
                )
        );
        if (!cooldownAcquired) {
            throw new OtpCooldownException();
        }

        String otp = generateSixDigitOtp();
        String otpHash = hmac(otp);

        redisTemplate.opsForValue().set(keys.otpKey(), otpHash, Duration.ofMinutes(otpTtlMinutes));
        redisTemplate.delete(keys.attemptKey());

        System.out.println(otp);
        emailSender.sendOtpEmail(normalizedEmail, otp, otpTtlMinutes);
    }

    public boolean isValid(String email, String otp) {
        String normalizedEmail = normalizeEmail(email);
        OtpKeys keys = keysFor(normalizedEmail);

        if (Boolean.TRUE.equals(redisTemplate.hasKey(keys.lockKey()))) {
            throw new OtpLockedException();
        }

        Long result = redisTemplate.execute(
                VERIFY_SCRIPT_OBJ,
                List.of(keys.otpKey(), keys.attemptKey(), keys.lockKey()),
                hmac(otp),
                String.valueOf(minutesToSeconds(otpTtlMinutes)),
                String.valueOf(maxAttempts),
                String.valueOf(minutesToSeconds(lockoutMinutes))
        );

        if (result == null) {
            throw new IllegalStateException("OTP verification failed unexpectedly.");
        }

        return switch (result.intValue()) {
            case 1 -> true;
            case -1 -> throw new OtpExpiredException();
            case -2 -> throw new OtpAttemptsExceededException();
            default -> false;
        };
    }

    // ---------- key building ----------

    private record OtpKeys(String otpKey, String cooldownKey, String attemptKey, String lockKey) {}

    private OtpKeys keysFor(String normalizedEmail) {
        String emailHash = emailHashUtil.hash(normalizedEmail);
        return new OtpKeys(
                KEY_PREFIX + emailHash,
                COOLDOWN_PREFIX + emailHash,
                ATTEMPT_PREFIX + emailHash,
                LOCK_PREFIX + emailHash
        );
    }

    // ---------- crypto helpers ----------

    private String generateSixDigitOtp() {
        int number = random.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Unable to compute OTP HMAC.", e);
        }
    }

    private String toHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private long minutesToSeconds(long minutes) {
        return minutes * 60;
    }

    // ---------- validation ----------

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email is required.");
        }
        if (!email.matches(UNIVERSITY_EMAIL_PATTERN)) {
            throw new IllegalArgumentException("Only university email addresses are valid.");
        }
        return email.trim().toLowerCase();
    }
}