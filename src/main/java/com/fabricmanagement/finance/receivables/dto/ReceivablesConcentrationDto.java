package com.fabricmanagement.finance.receivables.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Customer concentration entry")
public record ReceivablesConcentrationDto(
    @Schema(description = "Trading partner ID") UUID tradingPartnerId,
    @Schema(description = "Trading partner display name", example = "Acme Textiles")
        String tradingPartnerName,
    @Schema(description = "Net outstanding amount in reporting currency", example = "45000.0000")
        BigDecimal outstanding,
    @Schema(description = "Percentage of total net AR", example = "35.2500")
        BigDecimal percentOfTotal) {}
