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

/** Nominal knitting machine and stitch values of one article. */
@Entity
@Table(name = "prod_fabric_knitted", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FabricKnittedDetail extends BaseEntity {

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fabric_article_id", nullable = false, updatable = false)
  private FabricArticle article;

  @Column(name = "machine_gauge")
  private Integer machineGauge;

  @Column(name = "machine_diameter_in", precision = 5, scale = 1)
  private BigDecimal machineDiameterIn;

  @Column(name = "courses_per_cm", precision = 6, scale = 1)
  private BigDecimal coursesPerCm;

  @Column(name = "wales_per_cm", precision = 6, scale = 1)
  private BigDecimal walesPerCm;

  @Column(name = "stitch_length_mm", precision = 6, scale = 3)
  private BigDecimal stitchLengthMm;

  @Column(name = "feeders")
  private Integer feeders;

  FabricKnittedDetail(FabricArticle article) {
    this.article = article;
  }

  void apply(FabricArticleSpec.Knitted spec) {
    this.machineGauge = FabricArticleSpec.positiveOrNull("machineGauge", spec.machineGauge());
    this.machineDiameterIn =
        FabricArticleSpec.positiveOrNull("machineDiameterIn", spec.machineDiameterIn());
    this.coursesPerCm = FabricArticleSpec.positiveOrNull("coursesPerCm", spec.coursesPerCm());
    this.walesPerCm = FabricArticleSpec.positiveOrNull("walesPerCm", spec.walesPerCm());
    this.stitchLengthMm = FabricArticleSpec.positiveOrNull("stitchLengthMm", spec.stitchLengthMm());
    this.feeders = FabricArticleSpec.positiveOrNull("feeders", spec.feeders());
  }

  FabricArticleSpec.Knitted spec() {
    return new FabricArticleSpec.Knitted(
        machineGauge, machineDiameterIn, coursesPerCm, walesPerCm, stitchLengthMm, feeders);
  }

  @Override
  protected String getModuleCode() {
    return "FKNT";
  }
}
