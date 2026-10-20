package com.fabricmanagement.product.fabric.domain.article;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.fiber.domain.MaterialSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One fibre of the fabric's declared (label) composition, with the fibre's ISO code and name as
 * they were when declared (snapshot invariant, YARN-ARCHITECTURE Y5).
 */
@Entity
@Table(name = "prod_fabric_composition", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FabricCompositionLine extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fabric_article_id", nullable = false, updatable = false)
  private FabricArticle article;

  @Column(name = "fiber_id", nullable = false)
  private UUID fiberId;

  @Column(name = "fiber_iso_code", nullable = false, length = 10)
  private String fiberIsoCode;

  @Column(name = "fiber_name", nullable = false, length = 255)
  private String fiberName;

  @Enumerated(EnumType.STRING)
  @Column(name = "material_source", length = 20)
  private MaterialSource materialSource;

  @Column(name = "percentage", nullable = false, precision = 5, scale = 2)
  private BigDecimal percentage;

  FabricCompositionLine(FabricArticle article, FabricArticleSpec.CompositionLine line) {
    this.article = article;
    this.fiberId = line.fiberId();
    this.fiberIsoCode = line.isoCode();
    this.fiberName = line.fiberName();
    this.materialSource = line.materialSource();
    this.percentage = line.percentage();
  }

  void apply(FabricArticleSpec.CompositionLine line) {
    this.fiberIsoCode = line.isoCode();
    this.fiberName = line.fiberName();
    this.percentage = line.percentage();
  }

  FabricArticleSpec.CompositionLine line() {
    return new FabricArticleSpec.CompositionLine(
        fiberId, fiberIsoCode, fiberName, materialSource, percentage);
  }

  @Override
  protected String getModuleCode() {
    return "FCMP";
  }
}
