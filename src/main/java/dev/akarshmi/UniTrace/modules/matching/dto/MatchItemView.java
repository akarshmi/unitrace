package dev.akarshmi.UniTrace.modules.matching.dto;

import java.time.Instant;
import java.util.UUID;

import dev.akarshmi.UniTrace.modules.item.entity.ItemStatus;
import dev.akarshmi.UniTrace.modules.item.entity.ItemType;

/** What a student may see about the OTHER person's report: no name, no contact details. */
public record MatchItemView(
        UUID id,
        ItemType type,
        String title,
        String description,
        String location,
        String imageUrl,
        ItemStatus status,
        Instant eventFrom,
        Instant eventTo,
        Instant createdAt,
        Instant deskReceivedAt,
        Instant dropOffDeadline) {
}
