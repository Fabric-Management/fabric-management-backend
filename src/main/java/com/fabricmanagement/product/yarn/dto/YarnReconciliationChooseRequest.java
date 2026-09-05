package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.app.port.LegacyDesignationSourceKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Stable identity of the stored candidate to adopt verbatim")
public record YarnReconciliationChooseRequest(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull
        LegacyDesignationSourceKind sourceKind,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotBlank String sourceRecordId) {}
