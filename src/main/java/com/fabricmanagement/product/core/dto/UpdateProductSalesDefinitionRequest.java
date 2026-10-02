package com.fabricmanagement.product.core.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

/** Full replacement of a product's extra sales units and finished widths. */
@Schema(name = "UpdateProductSalesDefinitionRequest")
public record UpdateProductSalesDefinitionRequest(
    @NotNull @Size(max = 20) List<@NotBlank @Size(max = 20) String> extraSalesUnits,
    @NotNull @Size(max = 50) @Valid List<FinishedWidthInput> finishedWidths) {

  @Schema(name = "FinishedWidthInput")
  public record FinishedWidthInput(
      @NotNull @DecimalMin(value = "0.01") BigDecimal value,
      @NotNull @Pattern(regexp = "CM|IN|cm|in") String unit) {}
}
