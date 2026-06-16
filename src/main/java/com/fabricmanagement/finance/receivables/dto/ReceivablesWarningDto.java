package com.fabricmanagement.finance.receivables.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.UUID;

@Schema(description = "Non-fatal warning produced while building receivables insights")
public record ReceivablesWarningDto(
    @Schema(description = "Warning code", example = "MISSING_RATE") String code,
    @Schema(description = "Invoice that caused the warning") UUID invoiceId,
    @Schema(description = "Warning details") String message) {}
