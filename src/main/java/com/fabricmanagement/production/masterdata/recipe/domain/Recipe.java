package com.fabricmanagement.production.masterdata.recipe.domain;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.production.masterdata.recipe.domain.exception.RecipeDomainException;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Represents a blend instruction (Recipe). Doesn't hold any cost info. */
@Entity
@Table(name = "prod_recipe", schema = "production")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Recipe extends BaseEntity {

  @Column(name = "name", nullable = false, length = 500)
  private String name;

  @Column(name = "iso_code", nullable = false, length = 255)
  private String isoCode;

  @org.hibernate.annotations.Type(io.hypersistence.utils.hibernate.type.json.JsonType.class)
  @Column(name = "components", columnDefinition = "jsonb", nullable = false)
  @Builder.Default
  private List<RecipeComponentNode> components = new ArrayList<>();

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private RecipeStatus status;

  @Column(name = "recipe_version", nullable = false)
  private Integer recipeVersion;

  @Column(name = "parent_recipe_id")
  private UUID parentRecipeId;

  // Polymorphic hybrid design: we don't manage the 1:N directly through Hibernate
  // cascading to avoid JSONB vs JOIN complexities. The separate RecipeComponent table
  // is updated in tandem through the RecipeService.

  @Override
  protected String getModuleCode() {
    return "RCP";
  }

  /** Validates if the given components' percentages sum to precisely 100. */
  public void validatePercentageSum() {
    if (this.components == null || this.components.isEmpty()) {
      throw new RecipeDomainException("Recipe must have at least one component.");
    }

    BigDecimal total =
        components.stream()
            .map(RecipeComponentNode::getPercentage)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    if (total.compareTo(new BigDecimal("100.00")) != 0
        && total.compareTo(new BigDecimal("100.0")) != 0
        && total.compareTo(new BigDecimal("100")) != 0) {
      throw new RecipeDomainException(
          "Component percentages must sum up to exactly 100. Current sum: " + total);
    }
  }

  /**
   * Generates formulation representation to update name and ISO Code Rule for name: {percentage}%
   * {fiberName} {certification} {origin} (sorted descending by percentage) Rule for isoCode:
   * {fiberIsoCode} {percentage} / ...
   */
  public void recalculateNameAndIsoCode() {
    if (this.components == null || this.components.isEmpty()) {
      this.name = "Empty Recipe";
      this.isoCode = "NONE";
      return;
    }

    // Sort by percentage descending for presentation
    List<RecipeComponentNode> sorted = new ArrayList<>(this.components);
    sorted.sort(Comparator.comparing(RecipeComponentNode::getPercentage).reversed());

    this.name =
        sorted.stream()
            .map(
                c -> {
                  String cert =
                      (c.getCertification() != null && !c.getCertification().isBlank())
                          ? " " + c.getCertification()
                          : "";
                  String origin =
                      (c.getOrigin() != null && !c.getOrigin().isBlank())
                          ? " " + c.getOrigin()
                          : "";
                  // e.g. "60% Cotton GOTS TR"
                  return String.format(
                      "%s%% %s%s%s",
                      c.getPercentage().stripTrailingZeros().toPlainString(),
                      c.getFiberName(),
                      cert,
                      origin);
                })
            .collect(Collectors.joining(", "));

    this.isoCode =
        sorted.stream()
            .map(
                c ->
                    String.format(
                        "%s %s",
                        c.getFiberIsoCode(),
                        c.getPercentage().stripTrailingZeros().toPlainString()))
            .collect(Collectors.joining(" / "));
  }
}
