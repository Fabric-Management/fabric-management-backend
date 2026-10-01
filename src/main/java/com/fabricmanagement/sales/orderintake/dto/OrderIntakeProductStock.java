package com.fabricmanagement.sales.orderintake.dto;

import com.fabricmanagement.production.core.batch.api.query.ProposalStockQueryService.OfferableStockSummary;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.Map;
import java.util.stream.Collectors;

/** Advisory, all-colour stock for a catalogue product before the order's colour and width exist. */
@Schema(name = "OrderIntakeProductStock")
public record OrderIntakeProductStock(
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Offerable whole pieces by their recorded package type")
        Map<String, Long> packages,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "Exact kilograms of all offerable pieces; null if any lacks weight")
        BigDecimal kg,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            nullable = true,
            description = "Exact metres of all offerable pieces; null if any lacks length")
        BigDecimal metres,
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description =
                "Pieces whose availability still needs evidence; excluded from all totals")
        long unknownPieces) {

  public static OrderIntakeProductStock from(OfferableStockSummary summary) {
    return new OrderIntakeProductStock(
        summary.packages().entrySet().stream()
            .collect(Collectors.toMap(entry -> entry.getKey().name(), Map.Entry::getValue)),
        summary.kilograms(),
        summary.metres(),
        summary.unknownPieces());
  }
}
