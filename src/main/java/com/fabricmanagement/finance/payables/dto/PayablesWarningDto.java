package com.fabricmanagement.finance.payables.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Non-fatal warning produced while building payables insights")
public record PayablesWarningDto(
    @Schema(description = "Warning code", example = "MISSING_RATE") String code,
    @Schema(description = "Invoice that caused the warning") UUID invoiceId,
    @Schema(description = "Warning details") String message) {}
