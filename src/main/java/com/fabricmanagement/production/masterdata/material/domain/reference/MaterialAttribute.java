package com.fabricmanagement.production.masterdata.material.domain.reference;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

/**
 * Material Attribute - Reference table for material attributes.
 *
 * <p>Defines specific qualities of a material (production method, environmental status).
 *
 * <p><b>READ-ONLY:</b> System-defined, cannot be created/modified by tenants.
 *
 * <p>Can only be activated/deactivated.
 *
 * <p>A material can have multiple attributes (e.g., ORGANIC + FAIRTRADE).
 */
@Entity
@Table(
    name = "prod_material_attribute",
    schema = "production",
    indexes = {
      @Index(name = "idx_material_attr_code", columnList = "attribute_code"),
      @Index(name = "idx_material_attr_active", columnList = "is_active")
    })
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MaterialAttribute extends BaseEntity {

  @Column(name = "attribute_code", unique = true, nullable = false, length = 50, updatable = false)
  private String attributeCode;

  @Column(name = "attribute_name", nullable = false, length = 100)
  private String attributeName;

  @Column(name = "attribute_group", length = 50)
  private String attributeGroup;

  @Column(name = "material_scope", length = 20)
  private String materialScope;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "display_order")
  private Integer displayOrder;

  @Override
  protected String getModuleCode() {
    return "MATR";
  }
}
