package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "The stored candidate adopted to close a reconciliation row")
public record YarnReconciliationResolvedCandidateDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rawValue,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LegacyDesignationSourceKind sourceKind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceRecordId) {}
