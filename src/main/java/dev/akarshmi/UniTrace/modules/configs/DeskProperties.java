package dev.akarshmi.UniTrace.modules.configs;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Lost &amp; Found Desk / Security Office settings. */
@ConfigurationProperties(prefix = "unitrace.desk")
public record DeskProperties(
        /** HMAC secret for handover keys. Supply via env HANDOVER_SECRET; never commit a real value. */
        String handoverSecret,
        @DefaultValue("7d") Duration keyTtl,
        @DefaultValue("5") int maxKeyAttempts,
        /** Desk mailbox that receives "new potential match" mails. Blank disables those mails. */
        String deskEmail,
        @DefaultValue("Asia/Kolkata") String zone,
        /** Items must be handed in by this local time on the report day (otherwise the next day). */
        @DefaultValue("17:00") String dropOffTime) {

    public DeskProperties {
        if (handoverSecret == null || handoverSecret.isBlank()) {
            throw new IllegalArgumentException("unitrace.desk.handover-secret must be set (env HANDOVER_SECRET)");
        }
        if (maxKeyAttempts < 1) {
            throw new IllegalArgumentException("unitrace.desk.max-key-attempts must be >= 1");
        }
        ZoneId.of(zone);
        LocalTime.parse(dropOffTime);
    }

    public ZoneId zoneId() {
        return ZoneId.of(zone);
    }

    public LocalTime dropOffLocalTime() {
        return LocalTime.parse(dropOffTime);
    }
}
