package com.fabricmanagement.procurement.quote.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Dyeing and finishing specific quote specifications")
public record DyeQuoteSpecs(
    @Schema(description = "Color code or name") String color,
    @Schema(description = "Dyeing process type (e.g., Reactive, Disperse)") String processType,
    @Schema(description = "Finishing treatments (e.g., Sanforized, Water Repellent)") String finish,
    @Schema(description = "Expected shrinkage after wash") BigDecimal shrinkageExpected,
    @Schema(description = "Additional processing notes") String processNotes)
    implements SupplierQuoteSpecs {}
