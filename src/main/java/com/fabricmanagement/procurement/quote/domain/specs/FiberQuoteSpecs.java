package com.fabricmanagement.procurement.quote.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

@Schema(description = "Fiber-specific quote specifications")
public record FiberQuoteSpecs(
    @Schema(description = "Micronaire value") BigDecimal micronaire,
    @Schema(description = "Staple length in mm") BigDecimal stapleLengthMm,
    @Schema(description = "Strength in g/tex") BigDecimal strength,
    @Schema(description = "Trash content percentage") BigDecimal trashContentPercentage,
    @Schema(description = "Additional fiber quality notes") String qualityNotes)
    implements SupplierQuoteSpecs {}
