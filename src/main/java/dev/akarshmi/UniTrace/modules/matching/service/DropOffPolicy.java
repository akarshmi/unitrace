package dev.akarshmi.UniTrace.modules.matching.service;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

import dev.akarshmi.UniTrace.modules.configs.DeskProperties;

/** Items must be handed to the Security Office by ~5 PM on the report day (next day if reported after that). */
@Component
public class DropOffPolicy {

    private final ZoneId zone;
    private final LocalTime cutoff;

    public DropOffPolicy(DeskProperties props) {
        this.zone = props.zoneId();
        this.cutoff = props.dropOffLocalTime();
    }

    public Instant deadlineFor(Instant reportedAt) {
        if (reportedAt == null) {
            return null;
        }
        ZonedDateTime reported = reportedAt.atZone(zone);
        ZonedDateTime deadline = reported.toLocalDate().atTime(cutoff).atZone(zone);
        if (!reported.isBefore(deadline)) {
            deadline = deadline.plusDays(1);
        }
        return deadline.toInstant();
    }
}
