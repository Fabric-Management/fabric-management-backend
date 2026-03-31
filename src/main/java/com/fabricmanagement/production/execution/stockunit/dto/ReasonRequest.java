package com.fabricmanagement.production.execution.stockunit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Shared request body for hold, quarantine, release-hold, release-quarantine, dispose. */
public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}
