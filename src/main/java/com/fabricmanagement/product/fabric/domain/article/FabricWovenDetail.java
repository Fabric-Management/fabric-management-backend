package com.fabricmanagement.product.fabric.domain.article;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Nominal woven design values of one article; measured values live in the property registry. */
@Entity
@Table(name = "prod_fabric_woven", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FabricWovenDetail extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fabric_article_id", nullable = false, updatable = false)
  private FabricArticle article;

  @Column(name = "ends_per_cm", precision = 6, scale = 1)
  private BigDecimal endsPerCm;

  @Column(name = "picks_per_cm", precision = 6, scale = 1)
  private BigDecimal picksPerCm;

  @Column(name = "reed_notation", length = 40)
  private String reedNotation;

  @Column(name = "weave_notation", length = 40)
  private String weaveNotation;

  FabricWovenDetail(FabricArticle article) {
    this.article = article;
  }

  void apply(FabricArticleSpec.Woven spec) {
    this.endsPerCm = FabricArticleSpec.positiveOrNull("endsPerCm", spec.endsPerCm());
    this.picksPerCm = FabricArticleSpec.positiveOrNull("picksPerCm", spec.picksPerCm());
    this.reedNotation = FabricArticleSpec.trimToNull(spec.reedNotation());
    this.weaveNotation = FabricArticleSpec.trimToNull(spec.weaveNotation());
  }

  FabricArticleSpec.Woven spec() {
    return new FabricArticleSpec.Woven(endsPerCm, picksPerCm, reedNotation, weaveNotation);
  }

  @Override
  protected String getModuleCode() {
    return "FWOV";
  }
}
