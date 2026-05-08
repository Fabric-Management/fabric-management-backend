package com.fabricmanagement.procurement.quote.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Yarn-specific quote specifications")
public record YarnQuoteSpecs(
    @Schema(description = "Yarn count (e.g., Ne 30/1)") String yarnCount,
    @Schema(description = "Blend composition (e.g., 100% Cotton)") String blend,
    @Schema(description = "Twist level (TPM or TPI)") BigDecimal twist,
    @Schema(description = "Strength (RKM)") BigDecimal strength,
    @Schema(description = "Additional yarn notes") String yarnNotes)
    implements SupplierQuoteSpecs {}
