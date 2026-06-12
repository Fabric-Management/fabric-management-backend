package com.fabricmanagement.procurement.purchaseorder.domain.specs;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Fiber-specific purchase order specifications")
public record FiberPurchaseSpecs(
    @Schema(description = "Staple length (mm)", example = "28.5") Double stapleLength,
    @Schema(description = "Quality grade", example = "A") String grade,
    @Schema(description = "Moisture content %", example = "8.5") Double moistureContent,
    @Schema(description = "Micronaire (fineness+maturity)", example = "4.2") Double micronaire,
    @Schema(description = "Fiber strength (g/tex)", example = "30.5") Double strength,
    @Schema(description = "Length uniformity index (%)", example = "83.0") Double uniformityIndex,
    @Schema(description = "Trash/foreign matter content (%)", example = "1.2") Double trashContent,
    @Schema(description = "Color grade (Rd/+b)", example = "41-3") String colorGrade,
    @Schema(description = "Country/region of origin", example = "Turkey-Aegean") String origin,
    @Schema(description = "Certifications", example = "[\"BCI\",\"GOTS\"]")
        List<String> certifications,
    @Schema(description = "Crop year", example = "2025/2026") String cropYear)
    implements PurchaseOrderSpecs {}
