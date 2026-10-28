package com.fabricmanagement.sales.quote.dto;

import com.fabricmanagement.sales.common.domain.SalesQuantityPolicy;
import com.fabricmanagement.sales.orderintake.dto.DraftStockSelectionRequests;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class QuoteRollSelectionRequests {
  private QuoteRollSelectionRequests() {}

  @Schema(name = "ApplyQuoteRollSelectionRequest")
  public record Apply(
      @NotNull UUID operationId,
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotNull @Valid DraftStockSelectionRequests.Proof selection) {}

  @Schema(name = "CreateQuoteStockLineRequest")
  public record Create(
      @NotNull UUID operationId,
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotNull @Valid AddQuoteLineRequest line) {}

  @Schema(name = "RemoveQuoteRollSelectionRequest")
  public record Remove(@NotNull UUID operationId, @NotNull @PositiveOrZero Long expectedVersion) {}

  @Schema(name = "RecordSalesQuantityPolicyRequest")
  public record Policy(
      @NotNull UUID operationId,
      @NotNull @PositiveOrZero Long expectedVersion,
      @NotNull SalesQuantityPolicy quantityPolicy,
      @NotBlank @Size(max = 500) String reason) {}
}
