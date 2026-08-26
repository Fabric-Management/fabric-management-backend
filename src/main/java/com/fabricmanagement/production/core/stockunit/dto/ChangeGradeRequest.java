package com.fabricmanagement.production.core.stockunit.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record ChangeGradeRequest(
    @NotNull UUID newGradeId,
    @NotBlank String reason,
    UUID approvalId // nullable — required only for promotions
    ) {}
