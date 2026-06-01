package com.fabricmanagement.production.masterdata.recipe.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents a single fiber component within a recipe.
 *
 * <p>This entity is stored in a separate table (prod_recipe_component) for fast queries, reporting,
 * and rule engine filtering, even though the parent Recipe also stores this information in a JSONB
 * block for fast retrieval.
 *
 * <p>Uniqueness (active records only) is enforced via a partial DB index in Flyway migration (WHERE
 * is_active = true AND deleted_at IS NULL). JPA @UniqueConstraint is intentionally NOT used here
 * because JPA cannot express partial (conditional) unique constraints.
 */
@Entity
@Table(name = "prod_recipe_component", schema = "production")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecipeComponent extends BaseEntity {

  @Column(name = "recipe_id", nullable = false)
  private UUID recipeId;

  @Column(name = "fiber_id", nullable = false)
  private UUID fiberId;

  @Column(name = "fiber_name", nullable = false)
  private String fiberName;

  @Column(name = "fiber_iso_code", nullable = false, length = 10)
  private String fiberIsoCode;

  @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
  private BigDecimal percentage;

  @Column(name = "certification", length = 50)
  private String certification;

  @Column(name = "origin", length = 10)
  private String origin;

  @Column(name = "display_order", nullable = false)
  private Integer displayOrder;

  @Override
  protected String getModuleCode() {
    return "RCPC";
  }

  @PrePersist
  @PreUpdate
  protected void normalizeFields() {
    if (this.certification != null && !this.certification.isBlank()) {
      this.certification = this.certification.strip().toUpperCase(Locale.ROOT);
    } else {
      this.certification = null;
    }

    if (this.origin != null && !this.origin.isBlank()) {
      this.origin = this.origin.strip().toUpperCase(Locale.ROOT);
    } else {
      this.origin = null;
    }
  }

  public RecipeComponentNode toNode() {
    return RecipeComponentNode.builder()
        .fiberId(this.fiberId)
        .fiberName(this.fiberName)
        .fiberIsoCode(this.fiberIsoCode)
        .percentage(this.percentage)
        .certification(this.certification)
        .origin(this.origin)
        .build();
  }
}
