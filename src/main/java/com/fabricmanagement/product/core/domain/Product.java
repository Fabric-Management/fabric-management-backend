package com.fabricmanagement.product.core.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Product - master data for production products. Types: FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE.
 * The parent of the type-specific master records ({@code prod_fiber}, {@code prod_yarn_article}).
 */
@Entity
@Table(
    name = "prod_product",
    schema = "production",
    indexes = {
      @Index(name = "idx_product_tenant_type", columnList = "tenant_id,product_type"),
      @Index(name = "idx_product_active", columnList = "is_active")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Product extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "product_type", nullable = false, length = 20)
  private ProductType productType;

  @Column(name = "unit", nullable = false, length = 20)
  private String unit;

  /**
   * The product's own name (FABRIC-0). Fibre and yarn names stay authoritative in their master
   * records ({@code prod_fiber}, {@code prod_yarn_article}); for every other type this is the name
   * the tenant sees. Never blank: an empty name is stored as {@code null}.
   */
  @Column(name = "name", length = 255)
  private String name;

  /** Free description of the product; never blank, an empty one is stored as {@code null}. */
  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  public static Product create(ProductType type, String unit) {
    return Product.builder().productType(type).unit(unit).build();
  }

  public static Product create(ProductType type, String unit, String name, String description) {
    return Product.builder()
        .productType(type)
        .unit(unit)
        .name(trimToNull(name))
        .description(trimToNull(description))
        .build();
  }

  /**
   * Types whose name lives only on the product: there is no type-specific master record to carry
   * it, so a product of these types cannot be created without one.
   */
  public static boolean requiresOwnName(ProductType type) {
    return type != ProductType.FIBER && type != ProductType.YARN;
  }

  private static String trimToNull(String value) {
    if (value == null) return null;
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }

  @Override
  protected String getModuleCode() {
    return "PROD";
  }
}
