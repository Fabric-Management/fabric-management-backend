package com.fabricmanagement.product.yarn.domain.article;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.fiber.domain.Fiber;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import com.fabricmanagement.product.yarn.domain.exception.YarnDomainException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
    name = "prod_yarn_article_composition",
    schema = "production",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_yarn_article_composition_fiber",
            columnNames = {"tenant_id", "article_id", "fiber_id"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YarnArticleComposition extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "article_id", nullable = false, updatable = false)
  private YarnArticle article;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fiber_id", nullable = false, updatable = false)
  private Fiber fiber;

  @Column(name = "fiber_iso_code", nullable = false, length = 10, updatable = false)
  private String fiberIsoCode;

  @Column(name = "fiber_name", nullable = false, length = 255, updatable = false)
  private String fiberName;

  @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
  @Column(name = "material_source", length = 20, updatable = false)
  private MaterialSource materialSource;

  @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
  private BigDecimal percentage;

  static YarnArticleComposition create(YarnArticle article, Fiber fiber, BigDecimal percentage) {
    if (fiber == null || fiber.getId() == null) {
      throw new YarnDomainException("I14", "I14: composition requires a persisted Fiber");
    }
    if (!fiber.isPure()) {
      throw new YarnDomainException("I14", "I14: blend Fiber records are not composition inputs");
    }
    if (percentage == null || percentage.signum() <= 0) {
      throw new YarnDomainException("I14", "I14: composition percentage must be positive");
    }
    YarnArticleComposition row = new YarnArticleComposition();
    row.article = article;
    row.fiber = fiber;
    row.fiberIsoCode = fiber.getFiberIsoCode().getIsoCode();
    row.fiberName = fiber.getFiberName();
    row.materialSource = fiber.getMaterialSource();
    row.percentage = percentage.setScale(2, java.math.RoundingMode.HALF_UP);
    return row;
  }

  public UUID getFiberId() {
    return fiber == null ? null : fiber.getId();
  }

  @Override
  protected String getModuleCode() {
    return "YCOM";
  }
}
