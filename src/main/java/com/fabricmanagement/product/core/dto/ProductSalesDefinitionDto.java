package com.fabricmanagement.product.core.dto;

import com.fabricmanagement.product.core.domain.ProductType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * What a product may be sold as: its base unit, extra sales units and finished widths (SOI
 * R05/R06). Values are exact; no unit conversion is implied between them.
 */
@Schema(name = "ProductSalesDefinition")
public record ProductSalesDefinitionDto(
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID productId,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String uid,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) ProductType productType,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) String baseUnit,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean active,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<String> extraSalesUnits,
    @Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<FinishedWidthOption> finishedWidths) {

  public ProductSalesDefinitionDto {
    extraSalesUnits = extraSalesUnits == null ? List.of() : List.copyOf(extraSalesUnits);
    finishedWidths = finishedWidths == null ? List.of() : List.copyOf(finishedWidths);
  }

  /** Base unit first, then the extra units, without case-insensitive duplicates. */
  public List<String> allowedUnits() {
    java.util.LinkedHashMap<String, String> units = new java.util.LinkedHashMap<>();
    if (baseUnit != null && !baseUnit.isBlank()) {
      units.put(baseUnit.trim().toUpperCase(Locale.ROOT), baseUnit.trim());
    }
    extraSalesUnits.forEach(
        unit -> units.putIfAbsent(unit.trim().toUpperCase(Locale.ROOT), unit.trim()));
    return List.copyOf(units.values());
  }

  public boolean allowsUnit(String unit) {
    if (unit == null || unit.isBlank()) {
      return false;
    }
    String requested = unit.trim().toUpperCase(Locale.ROOT);
    return allowedUnits().stream()
        .anyMatch(allowed -> allowed.toUpperCase(Locale.ROOT).equals(requested));
  }

  public boolean definesFinishedWidths() {
    return !finishedWidths.isEmpty();
  }

  public boolean allowsFinishedWidth(BigDecimal value, String unit) {
    if (value == null || unit == null) {
      return false;
    }
    String requestedUnit = unit.trim().toUpperCase(Locale.ROOT);
    return finishedWidths.stream()
        .anyMatch(
            option -> option.value().compareTo(value) == 0 && option.unit().equals(requestedUnit));
  }

  @Schema(name = "FinishedWidthOption")
  public record FinishedWidthOption(
      @Schema(requiredMode = Schema.RequiredMode.REQUIRED) BigDecimal value,
      @Schema(
              requiredMode = Schema.RequiredMode.REQUIRED,
              allowableValues = {"CM", "IN"})
          String unit) {}
}
