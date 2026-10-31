package dev.akarshmi.UniTrace.modules.matching.dto;

import java.time.Instant;
import java.util.UUID;

import dev.akarshmi.UniTrace.modules.item.entity.ItemStatus;
import dev.akarshmi.UniTrace.modules.item.entity.ItemType;

/** Full item view for desk staff, including reporter identity for verification. */
public record DeskItemView(
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
        Instant dropOffDeadline,
        Instant deskReceivedAt,
        String reporterName,
        Long reporterRegistrationNumber,
        String reporterUniEmail) {
}
