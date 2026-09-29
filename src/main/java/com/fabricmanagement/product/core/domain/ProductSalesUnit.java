package com.fabricmanagement.product.core.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A sales unit allowed for a product in addition to its base unit (SOI R06). Units are never
 * converted into each other: metre and kilogram quantities stay separate.
 */
@Entity
@Table(name = "prod_product_sales_unit", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductSalesUnit extends BaseEntity {

  @Column(name = "product_id", nullable = false, updatable = false)
  private UUID productId;

  @Column(name = "unit", nullable = false, length = 20, updatable = false)
  private String unit;

  public static ProductSalesUnit of(UUID productId, String unit) {
    if (productId == null) {
      throw new IllegalArgumentException("Product is required");
    }
    if (unit == null || unit.isBlank()) {
      throw new IllegalArgumentException("Sales unit is required");
    }
    ProductSalesUnit salesUnit = new ProductSalesUnit();
    salesUnit.productId = productId;
    salesUnit.unit = unit.trim();
    return salesUnit;
  }

  @Override
  protected String getModuleCode() {
    return "PSU";
  }
}
