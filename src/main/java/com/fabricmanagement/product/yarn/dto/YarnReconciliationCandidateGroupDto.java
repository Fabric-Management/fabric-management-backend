package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "Byte-equal candidate spelling grouped with its occurrence count")
public record YarnReconciliationCandidateGroupDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String rawValue,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long occurrences,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) LegacyDesignationSourceKind sourceKind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant recordedAt,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String sourceRecordId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean overlength) {}
