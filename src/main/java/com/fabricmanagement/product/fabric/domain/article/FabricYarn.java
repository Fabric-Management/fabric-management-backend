package com.fabricmanagement.product.fabric.domain.article;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.fabric.domain.vocabulary.FabricYarnRole;
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

/** One yarn of a fabric: its role, order within the role and share of the fabric's mass. */
@Entity
@Table(name = "prod_fabric_yarn", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FabricYarn extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "fabric_article_id", nullable = false, updatable = false)
  private FabricArticle article;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private FabricYarnRole role;

  @Column(name = "position", nullable = false)
  private short position;

  @Column(name = "yarn_product_id", nullable = false)
  private UUID yarnProductId;

  @Column(name = "share_percent", nullable = false, precision = 5, scale = 2)
  private BigDecimal sharePercent;

  @Column(name = "yarn_designation_snapshot", length = 255)
  private String yarnDesignationSnapshot;

  FabricYarn(FabricArticle article, FabricArticleSpec.YarnLine line, short position) {
    this.article = article;
    this.role = line.role();
    this.position = position;
    this.yarnProductId = line.yarnProductId();
    this.sharePercent = line.sharePercent();
    this.yarnDesignationSnapshot = line.designation();
  }

  void apply(FabricArticleSpec.YarnLine line) {
    this.yarnProductId = line.yarnProductId();
    this.sharePercent = line.sharePercent();
    this.yarnDesignationSnapshot = line.designation();
  }

  FabricArticleSpec.YarnLine line() {
    return new FabricArticleSpec.YarnLine(
        role, yarnProductId, sharePercent, yarnDesignationSnapshot);
  }

  @Override
  protected String getModuleCode() {
    return "FYRN";
  }
}
