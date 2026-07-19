package com.fabricmanagement.production.execution.batch.app;

import com.fabricmanagement.production.execution.batch.domain.Batch;
import com.fabricmanagement.production.execution.batch.domain.PrimaryMeasure;
import com.fabricmanagement.production.masterdata.product.domain.ProductType;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** Canonical, stable measure and unit rules for batch quantities. */
@Service
public class BatchPrimaryMeasureService {

  private static final BigDecimal ONE_THOUSAND = new BigDecimal("1000");
  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  public Resolution resolve(Batch batch) {
    if (batch == null) {
      throw new IllegalArgumentException("Batch is required");
    }
    return resolve(batch.getProductType());
  }

  public Resolution resolve(ProductType productType) {
    PrimaryMeasure measure = primaryMeasure(productType);
    return new Resolution(measure, canonicalUnit(measure));
  }

  public PrimaryMeasure primaryMeasure(ProductType productType) {
    if (productType == null) {
      throw new IllegalArgumentException("Product type is required");
    }
    return switch (productType) {
      case FABRIC -> PrimaryMeasure.LENGTH;
      case FIBER, YARN -> PrimaryMeasure.WEIGHT;
      case CHEMICAL, CONSUMABLE ->
          throw new IllegalArgumentException(
              "Unsupported product type for primary measure: " + productType);
    };
  }

  public String canonicalUnit(PrimaryMeasure measure) {
    if (measure == null) {
      throw new IllegalArgumentException("Primary measure is required");
    }
    return measure == PrimaryMeasure.LENGTH ? "M" : "KG";
  }

  public String normalizeUnit(String unit) {
    return unit == null ? null : unit.trim().toUpperCase(Locale.ROOT);
  }

  /** Converts an exact same-dimension metric quantity into the canonical unit. */
  public Optional<BigDecimal> toCanonical(
      BigDecimal quantity, String sourceUnit, PrimaryMeasure measure) {
    if (quantity == null) {
      return Optional.empty();
    }
    String unit = normalizeUnit(sourceUnit);
    if (unit == null || unit.isBlank()) {
      return Optional.empty();
    }
    return switch (measure) {
      case WEIGHT ->
          switch (unit) {
            case "KG" -> Optional.of(quantity);
            case "MT" -> Optional.of(quantity.multiply(ONE_THOUSAND));
            case "G" -> Optional.of(quantity.divide(ONE_THOUSAND));
            default -> Optional.empty();
          };
      case LENGTH ->
          switch (unit) {
            case "M" -> Optional.of(quantity);
            case "CM" -> Optional.of(quantity.divide(ONE_HUNDRED));
            case "MM" -> Optional.of(quantity.divide(ONE_THOUSAND));
            default -> Optional.empty();
          };
    };
  }

  /** Converts a canonical quantity back to a compatible batch bookkeeping unit. */
  public Optional<BigDecimal> fromCanonical(
      BigDecimal canonicalQuantity, String targetUnit, PrimaryMeasure measure) {
    if (canonicalQuantity == null) {
      return Optional.empty();
    }
    String unit = normalizeUnit(targetUnit);
    if (unit == null || unit.isBlank()) {
      return Optional.empty();
    }
    return switch (measure) {
      case WEIGHT ->
          switch (unit) {
            case "KG" -> Optional.of(canonicalQuantity);
            case "MT" -> Optional.of(canonicalQuantity.divide(ONE_THOUSAND));
            case "G" -> Optional.of(canonicalQuantity.multiply(ONE_THOUSAND));
            default -> Optional.empty();
          };
      case LENGTH ->
          switch (unit) {
            case "M" -> Optional.of(canonicalQuantity);
            case "CM" -> Optional.of(canonicalQuantity.multiply(ONE_HUNDRED));
            case "MM" -> Optional.of(canonicalQuantity.multiply(ONE_THOUSAND));
            default -> Optional.empty();
          };
    };
  }

  public record Resolution(PrimaryMeasure primaryMeasure, String primaryUnit) {}
}
