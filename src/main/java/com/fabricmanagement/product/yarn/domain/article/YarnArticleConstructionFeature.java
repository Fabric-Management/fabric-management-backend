package com.fabricmanagement.product.yarn.domain.article;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.yarn.domain.vocabulary.YarnConstructionFeature;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "prod_yarn_article_construction_feature",
    schema = "production",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uq_yarn_article_feature",
            columnNames = {"tenant_id", "article_id", "feature"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YarnArticleConstructionFeature extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "article_id", nullable = false, updatable = false)
  private YarnArticle article;

  @Enumerated(EnumType.STRING)
  @Column(name = "feature", nullable = false, length = 30, updatable = false)
  private YarnConstructionFeature feature;

  static YarnArticleConstructionFeature create(
      YarnArticle article, YarnConstructionFeature feature) {
    YarnArticleConstructionFeature row = new YarnArticleConstructionFeature();
    row.article = article;
    row.feature = feature;
    return row;
  }

  @Override
  protected String getModuleCode() {
    return "YFTR";
  }
}
