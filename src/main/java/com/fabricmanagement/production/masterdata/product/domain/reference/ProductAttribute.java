package com.fabricmanagement.production.masterdata.product.domain.reference;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Product Attribute - Reference table for product attributes.
 *
 * <p>Defines specific qualities of a product (production method, environmental status).
 *
 * <p><b>READ-ONLY:</b> System-defined, cannot be created/modified by tenants.
 *
 * <p>Can only be activated/deactivated.
 *
 * <p>A product can have multiple attributes (e.g., ORGANIC + FAIRTRADE).
 */
@Entity
@Table(
    name = "prod_product_attribute",
    schema = "production",
    indexes = {
      @Index(name = "idx_product_attr_code", columnList = "attribute_code"),
      @Index(name = "idx_product_attr_active", columnList = "is_active")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductAttribute extends BaseEntity {

  @Column(name = "attribute_code", unique = true, nullable = false, length = 50, updatable = false)
  private String attributeCode;

  @Column(name = "attribute_name", nullable = false, length = 100)
  private String attributeName;

  @Column(name = "attribute_group", length = 50)
  private String attributeGroup;

  @Column(name = "product_scope", length = 20)
  private String productScope;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Override
  protected String getModuleCode() {
    return "PATTR";
  }
}
