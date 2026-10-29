package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.common.dto.SalesQuantityTermsDto;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Schema(
    name = "QuoteRollSelectionResult",
    description = "Immutable result of a versioned quote stock command")
public record QuoteRollSelectionDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID quoteId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID lineId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) long quoteVersion,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal commercialQuantity,
    @Schema(nullable = true) BigDecimal customerRequestQuantity,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) SalesQuantityTermsDto quantityTerms,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<UUID> heldPieceIds) {
  public QuoteRollSelectionDto {
    heldPieceIds = List.copyOf(heldPieceIds);
  }
}
