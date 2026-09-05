package dev.akarshmi.UniTrace.modules.item.dto;

import dev.akarshmi.UniTrace.modules.item.entity.ItemStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateItemStatusRequest(
        @NotNull(message = "Status is required")
        ItemStatus status
) {}