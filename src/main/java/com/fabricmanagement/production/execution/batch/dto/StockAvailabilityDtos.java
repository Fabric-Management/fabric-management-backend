package com.fabricmanagement.production.execution.batch.dto;

import com.fabricmanagement.production.execution.batch.domain.BatchUnitMismatchSource;
import com.fabricmanagement.production.execution.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.execution.stockunit.domain.PackageType;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** OpenAPI DTO family for the production stock-availability read model. */
public final class StockAvailabilityDtos {

  private StockAvailabilityDtos() {}

  @Schema(
      name = "ProductionStockAvailabilityProduct",
      description = "Product identity for an availability row")
  public record Product(
      @Schema(description = "Product identifier", requiredMode = Schema.RequiredMode.REQUIRED)
          UUID id,
      @Schema(description = "Product type", requiredMode = Schema.RequiredMode.REQUIRED)
          ProductType type) {}

  @Schema(
      name = "ProductionStockAvailabilityColour",
      description = "Batch-level colour-card reference")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Colour(
      @Schema(description = "Colour-card identifier", requiredMode = Schema.RequiredMode.REQUIRED)
          UUID id,
      @Schema(description = "Tenant colour code", requiredMode = Schema.RequiredMode.REQUIRED)
          String code,
      @Schema(
              description = "Human-readable colour name",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String name,
      @Schema(
              description = "Optional screen approximation in hexadecimal form",
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true)
          String hex) {}

  @Schema(
      name = "ProductionStockAvailabilityPhysical",
      description = "Physical stock vector; null means not measured")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Physical(
      @Schema(
              description = "Selectable physical weight in canonical kilograms",
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true)
          BigDecimal kg,
      @Schema(
              description = "Selectable physical length in canonical metres",
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true)
          BigDecimal metres,
      @Schema(
              description = "Selectable physical piece count; zero is a measured value",
              requiredMode = Schema.RequiredMode.REQUIRED)
          long pieceCount) {}

  @Schema(
      name = "ProductionStockAvailabilityUnitMismatch",
      description = "Quantity excluded from canonical arithmetic")
  public record UnitMismatch(
      @Schema(
              description = "Source of the incompatible quantity",
              requiredMode = Schema.RequiredMode.REQUIRED)
          BatchUnitMismatchSource source,
      @Schema(
              description = "Normalised incompatible source unit",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String unit,
      @Schema(
              description = "Quantity summed only within this source and unit",
              requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal quantity,
      @Schema(
              description = "Number of contributing rows",
              requiredMode = Schema.RequiredMode.REQUIRED)
          long rowCount) {}

  @Schema(
      name = "ProductionStockAvailabilityPieceBreakdown",
      description = "Selectable pieces grouped by package type")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record PieceBreakdown(
      @Schema(description = "Physical package type", requiredMode = Schema.RequiredMode.REQUIRED)
          PackageType packageType,
      @Schema(description = "Selectable piece count", requiredMode = Schema.RequiredMode.REQUIRED)
          long count,
      @Schema(
              description = "Canonical quantity in the product primary measure",
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true)
          BigDecimal totalPrimaryQuantity) {}

  @Schema(
      name = "ProductionStockAvailabilityQualityGrade",
      description = "Quality-grade reference for physical pieces")
  public record QualityGrade(
      @Schema(description = "Quality-grade identifier", requiredMode = Schema.RequiredMode.REQUIRED)
          UUID id,
      @Schema(
              description = "Tenant quality-grade code",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String code,
      @Schema(
              description = "Human-readable quality-grade name",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String name,
      @Schema(
              description = "Natural order; lower rank is better",
              requiredMode = Schema.RequiredMode.REQUIRED)
          int rank,
      @Schema(
              description = "Whether this grade may be sold",
              requiredMode = Schema.RequiredMode.REQUIRED)
          boolean saleable) {}

  @Schema(
      name = "ProductionStockAvailabilityQualityBreakdown",
      description = "Selectable pieces grouped by quality grade")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record QualityBreakdown(
      @Schema(
              description = "Quality grade; null represents UNASSIGNED pieces",
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true)
          QualityGrade grade,
      @Schema(
              description = "Selectable piece count in the grade",
              requiredMode = Schema.RequiredMode.REQUIRED)
          long pieceCount,
      @Schema(
              description = "Selectable grade weight in canonical kilograms",
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true)
          BigDecimal kg,
      @Schema(
              description = "Selectable grade length in canonical metres",
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true)
          BigDecimal metres) {}

  @Schema(
      name = "ProductionStockAvailabilitySummary",
      description = "Whole-filter-set availability totals for one product")
  public record Summary(
      @Schema(description = "Product identity", requiredMode = Schema.RequiredMode.REQUIRED)
          Product product,
      @Schema(
              description = "Canonical primary measure",
              requiredMode = Schema.RequiredMode.REQUIRED)
          PrimaryMeasure primaryMeasure,
      @Schema(
              description = "Canonical primary unit (M or KG)",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String primaryMeasureUnit,
      @Schema(
              description = "Number of matching saleable lots",
              requiredMode = Schema.RequiredMode.REQUIRED)
          long lotCount,
      @Schema(description = "Summed physical vector", requiredMode = Schema.RequiredMode.REQUIRED)
          Physical physical,
      @Schema(
              description = "Active lot-intent quantity in the canonical unit",
              requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal softIntent,
      @Schema(
              description = "Active hard-reservation quantity in the canonical unit",
              requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal hardReserved,
      @Schema(
              description = "Non-negative free quantity in the canonical unit",
              requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal free,
      @Schema(
              description = "True when any contributing lot had negative raw free stock",
              requiredMode = Schema.RequiredMode.REQUIRED)
          boolean overCommitted,
      @Schema(
              description = "Excluded incompatible quantities grouped by source and unit",
              requiredMode = Schema.RequiredMode.REQUIRED)
          List<UnitMismatch> unitMismatches,
      @Schema(
              description = "Selectable physical stock grouped by quality grade",
              requiredMode = Schema.RequiredMode.REQUIRED)
          List<QualityBreakdown> qualityBreakdown) {}

  @Schema(
      name = "ProductionStockAvailabilityLot",
      description = "Lot-level stock availability and physical breakdowns")
  @JsonInclude(JsonInclude.Include.ALWAYS)
  public record Lot(
      @Schema(description = "Batch/lot identifier", requiredMode = Schema.RequiredMode.REQUIRED)
          UUID batchId,
      @Schema(description = "Tenant lot number", requiredMode = Schema.RequiredMode.REQUIRED)
          String lotNo,
      @Schema(
              description = "Current batch lifecycle status",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String status,
      @Schema(description = "Product identity", requiredMode = Schema.RequiredMode.REQUIRED)
          Product product,
      @Schema(
              description = "Batch colour; null for explicitly colourless stock",
              requiredMode = Schema.RequiredMode.REQUIRED,
              nullable = true)
          Colour colour,
      @Schema(
              description = "Canonical primary measure",
              requiredMode = Schema.RequiredMode.REQUIRED)
          PrimaryMeasure primaryMeasure,
      @Schema(
              description = "Canonical primary unit (M or KG)",
              requiredMode = Schema.RequiredMode.REQUIRED)
          String primaryMeasureUnit,
      @Schema(description = "Physical stock vector", requiredMode = Schema.RequiredMode.REQUIRED)
          Physical physical,
      @Schema(
              description = "Whether physical values come from pieces or the batch fallback",
              requiredMode = Schema.RequiredMode.REQUIRED)
          PhysicalSource physicalSource,
      @Schema(
              description = "Active lot-intent quantity in the canonical unit",
              requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal softIntent,
      @Schema(
              description = "Active hard-reservation quantity in the canonical unit",
              requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal hardReserved,
      @Schema(
              description = "Non-negative lot-grain free quantity in the canonical unit",
              requiredMode = Schema.RequiredMode.REQUIRED)
          BigDecimal free,
      @Schema(
              description = "True when raw lot-grain free quantity was negative",
              requiredMode = Schema.RequiredMode.REQUIRED)
          boolean overCommitted,
      @Schema(
              description = "Excluded incompatible quantities grouped by source and unit",
              requiredMode = Schema.RequiredMode.REQUIRED)
          List<UnitMismatch> unitMismatches,
      @Schema(
              description = "Selectable pieces grouped by package type",
              requiredMode = Schema.RequiredMode.REQUIRED)
          List<PieceBreakdown> pieceBreakdown,
      @Schema(
              description = "Selectable pieces grouped by quality grade",
              requiredMode = Schema.RequiredMode.REQUIRED)
          List<QualityBreakdown> qualityBreakdown) {}

  @Schema(name = "ProductionStockAvailabilityPhysicalSource")
  public enum PhysicalSource {
    PIECES,
    BATCH_FALLBACK
  }
}
