package dev.akarshmi.UniTrace.modules.auth.dto;

import java.time.LocalDateTime;

public record ErrorResponse(
        String message,
        String status,
        int statusCode,
        String timestamp,
        String path
) {}