package com.fabricmanagement.product.yarn.domain.article;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import com.fabricmanagement.product.yarn.domain.vocabulary.CountSystem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "prod_yarn_article_structure_component",
    schema = "production",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_yarn_article_component_index",
            columnNames = {"tenant_id", "article_id", "kind", "component_index"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YarnArticleStructureComponent extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "article_id", nullable = false, updatable = false)
  private YarnArticle article;

  @Enumerated(EnumType.STRING)
  @Column(name = "kind", nullable = false, length = 20, updatable = false)
  private ComponentKind kind;

  @Column(name = "component_index", nullable = false, updatable = false)
  private int componentIndex;

  @Enumerated(EnumType.STRING)
  @Column(name = "layer_role", length = 20, updatable = false)
  private LayerRole layerRole;

  @Enumerated(EnumType.STRING)
  @Column(name = "component_count_system", length = 20, updatable = false)
  private CountSystem componentCountSystem;

  @Column(name = "component_count_value", precision = 18, scale = 6, updatable = false)
  private BigDecimal componentCountValue;

  @Column(name = "component_linear_density_tex", precision = 18, scale = 2, updatable = false)
  private BigDecimal componentLinearDensityTex;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "fiber_id", updatable = false)
  private Fiber fiber;

  @Column(name = "fiber_iso_code", length = 10, updatable = false)
  private String fiberIsoCode;

  @Column(name = "fiber_name", length = 255, updatable = false)
  private String fiberName;

  @Enumerated(EnumType.STRING)
  @Column(name = "material_source", length = 20, updatable = false)
  private MaterialSource materialSource;

  @Column(name = "label", length = 100, updatable = false)
  private String label;

  static YarnArticleStructureComponent create(
      YarnArticle article, YarnArticleSpec.ComponentInput input) {
    if (input.kind() == null || input.componentIndex() < 1) {
      throw new YarnDomainException("I4", "I4: component kind and positive index are required");
    }
    boolean systemPresent = input.componentCountSystem() != null;
    boolean valuePresent = input.componentCountValue() != null;
    if (systemPresent != valuePresent) {
      throw new YarnDomainException("I30", "I30: component count system/value must be paired");
    }
    if (valuePresent && input.componentCountValue().signum() <= 0) {
      throw new YarnDomainException("I29", "I29: component count value must be positive");
    }
    if (input.kind() == ComponentKind.LAYER && (systemPresent || valuePresent)) {
      throw new YarnDomainException("I27", "I27: LAYER rows cannot carry count values");
    }
    if (input.kind() == ComponentKind.LAYER && input.layerRole() == null) {
      throw new YarnDomainException("I23", "I23: a LAYER row requires layerRole");
    }
    if (input.kind() == ComponentKind.STRAND && input.layerRole() != null) {
      throw new YarnDomainException("I4", "I4: a STRAND row cannot carry layerRole");
    }
    if (input.fiber() != null && !input.fiber().isPure()) {
      throw new YarnDomainException("I14", "I14: component Fiber must be pure");
    }

    YarnArticleStructureComponent row = new YarnArticleStructureComponent();
    row.article = article;
    row.kind = input.kind();
    row.componentIndex = input.componentIndex();
    row.layerRole = input.layerRole();
    row.componentCountSystem = input.componentCountSystem();
    row.componentCountValue = input.componentCountValue();
    row.componentLinearDensityTex =
        input.kind() == ComponentKind.STRAND
            ? YarnArticleDerivation.componentTex(
                input.componentCountSystem(), input.componentCountValue())
            : null;
    row.fiber = input.fiber();
    if (input.fiber() != null) {
      row.fiberIsoCode = input.fiber().getFiberIsoCode().getIsoCode();
      row.fiberName = input.fiber().getFiberName();
      row.materialSource = input.fiber().getMaterialSource();
    }
    row.label = trimToNull(input.label());
    return row;
  }

  public UUID getFiberId() {
    return fiber == null ? null : fiber.getId();
  }

  private static String trimToNull(String value) {
    return value == null || value.isBlank() ? null : value.trim();
  }

  @Override
  protected String getModuleCode() {
    return "YCMP";
  }
}
