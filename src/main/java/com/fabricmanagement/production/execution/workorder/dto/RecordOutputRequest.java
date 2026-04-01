package com.fabricmanagement.production.execution.workorder.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record RecordOutputRequest(
    @NotNull(message = "stockUnitId is required") UUID stockUnitId, String notes) {}
