package dev.akarshmi.UniTrace.modules.item.dto;

import dev.akarshmi.UniTrace.modules.item.entity.Item;
import dev.akarshmi.UniTrace.modules.item.entity.ItemStatus;
import dev.akarshmi.UniTrace.modules.item.entity.ItemType;

import java.time.Instant;
import java.util.UUID;

public record ItemResponse(
        UUID id,
        ItemType type,
        String title,
        String description,
        ItemStatus status,
        String location,
//        GeoLocation geoLocation,
        String imageUrl,
        String reportedByName,
        Instant createdAt
) {
    public static ItemResponse from(Item item) {
        return new ItemResponse(
                item.getId(),
                item.getType(),
                item.getTitle(),
                item.getDescription(),
                item.getStatus(),
                item.getLocation(),
                item.getImageUrl(),
                item.getReportedBy().getFullName(),
                item.getCreatedAt()
        );
    }
}