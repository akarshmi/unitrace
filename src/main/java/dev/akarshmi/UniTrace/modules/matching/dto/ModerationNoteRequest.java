package dev.akarshmi.UniTrace.modules.matching.dto;

import jakarta.validation.constraints.Size;

public record ModerationNoteRequest(@Size(max = 500) String note) {
}
