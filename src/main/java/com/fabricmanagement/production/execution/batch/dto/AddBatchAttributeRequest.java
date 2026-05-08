package com.fabricmanagement.production.execution.batch.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import lombok.Builder;

@Builder
public record AddBatchAttributeRequest(
    @NotNull(message = "Attribute ID is required") UUID attributeId, String value) {}
