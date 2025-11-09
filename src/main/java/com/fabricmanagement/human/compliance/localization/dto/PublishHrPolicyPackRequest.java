package com.fabricmanagement.human.compliance.localization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublishHrPolicyPackRequest(
    @NotNull
    Instant effectiveFrom,
    Instant effectiveTo,
    String diffSnapshot
) {
}

