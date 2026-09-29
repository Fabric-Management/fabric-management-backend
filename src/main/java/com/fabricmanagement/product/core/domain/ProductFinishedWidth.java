package com.fabricmanagement.product.core.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A finished width a product is sold in (SOI R05). An order line may only name a width that the
 * product defines; there is no conversion between units and no nominal-to-finished derivation.
 */
@Entity
@Table(name = "prod_product_finished_width", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductFinishedWidth extends BaseEntity {

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(name = "width_value", nullable = false, precision = 8, scale = 2, updatable = false)
  private BigDecimal widthValue;

  @Column(name = "width_unit", nullable = false, length = 10, updatable = false)
  private String widthUnit;

  public static ProductFinishedWidth of(UUID productId, BigDecimal value, String unit) {
    if (productId == null) {
      throw new IllegalArgumentException("Product is required");
    }
    ProductFinishedWidth width = new ProductFinishedWidth();
    width.productId = productId;
    width.widthValue = normaliseValue(value);
    width.widthUnit = normaliseUnit(unit);
    return width;
  }

  /** True when this option names exactly the given width (value and unit, no conversion). */
  public boolean matches(BigDecimal value, String unit) {
    return value != null
        && unit != null
        && widthValue.compareTo(value) == 0
        && widthUnit.equals(unit.trim().toUpperCase(Locale.ROOT));
  }

  public static BigDecimal normaliseValue(BigDecimal value) {
    if (value == null || value.signum() <= 0) {
      throw new IllegalArgumentException("Finished width must be positive");
    }
    if (value.stripTrailingZeros().scale() > 2) {
      throw new IllegalArgumentException("Finished width allows at most two decimals");
    }
    return value.setScale(2, java.math.RoundingMode.UNNECESSARY);
  }

  public static String normaliseUnit(String unit) {
    if (unit == null) {
      throw new IllegalArgumentException("Finished width unit is required");
    }
    String normalised = unit.trim().toUpperCase(Locale.ROOT);
    if (!normalised.equals("CM") && !normalised.equals("IN")) {
      throw new IllegalArgumentException("Finished width unit must be CM or IN");
    }
    return normalised;
  }

  @Override
  protected String getModuleCode() {
    return "PFW";
  }
}
