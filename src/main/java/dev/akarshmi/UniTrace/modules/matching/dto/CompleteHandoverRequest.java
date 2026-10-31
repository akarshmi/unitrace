package dev.akarshmi.UniTrace.modules.matching.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Filled in by the desk officer after checking the student's university ID. */
public record CompleteHandoverRequest(
        @NotBlank @Size(max = 20) String handoverKey,
        @NotNull Long verifiedRegistrationNumber) {
}
