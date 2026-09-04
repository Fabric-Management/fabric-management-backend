package com.fabricmanagement.product.yarn.domain.backfill;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
import com.fabricmanagement.product.core.domain.Product;
import com.fabricmanagement.product.yarn.domain.article.YarnArticle;
import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "prod_yarn_backfill_reconciliation", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YarnBackfillReconciliation extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "product_id", nullable = false, updatable = false)
  private Product product;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "article_id", nullable = false, updatable = false)
  private YarnArticle article;

  @Enumerated(EnumType.STRING)
  @Column(name = "reason", nullable = false, length = 20, updatable = false)
  private YarnBackfillQueueReason reason;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private YarnBackfillQueueStatus status;

  @Type(JsonType.class)
  @Column(name = "candidates", nullable = false, columnDefinition = "jsonb", updatable = false)
  private JsonNode candidates;

  public static YarnBackfillReconciliation open(
      Product product, YarnArticle article, YarnBackfillQueueReason reason, JsonNode candidates) {
    YarnBackfillReconciliation row = new YarnBackfillReconciliation();
    row.product = Objects.requireNonNull(product, "product");
    row.article = Objects.requireNonNull(article, "article");
    row.reason = Objects.requireNonNull(reason, "reason");
    row.status = YarnBackfillQueueStatus.OPEN;
    row.candidates = Objects.requireNonNull(candidates, "candidates").deepCopy();
    return row;
  }

  public UUID getProductId() {
    return product == null ? null : product.getId();
  }

  public UUID getArticleId() {
    return article == null ? null : article.getId();
  }

  @Override
  protected String getModuleCode() {
    return "YBFR";
  }
}
