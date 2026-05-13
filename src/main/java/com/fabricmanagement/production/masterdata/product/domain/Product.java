package com.fabricmanagement.production.masterdata.product.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Product - master data for production products. Types: FIBER, YARN, FABRIC, CHEMICAL, CONSUMABLE
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

  public static Product create(ProductType type, String unit) {
    return Product.builder().productType(type).unit(unit).build();
  }

  @Override
  protected String getModuleCode() {
    return "PROD";
  }
}
