package com.fabricmanagement.production.execution.batch.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Request to assign or clear a batch color card")
public record UpdateBatchColorRequest(
    @Schema(
            description = "Active tenant color-card ID; null clears the batch color",
            nullable = true)
        UUID colorId) {}
