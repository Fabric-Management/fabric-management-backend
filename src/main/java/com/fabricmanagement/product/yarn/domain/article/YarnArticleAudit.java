package com.fabricmanagement.product.yarn.domain.article;

import com.fabricmanagement.common.infrastructure.persistence.BaseEntity;
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
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;

@Entity
@Table(name = "prod_yarn_article_audit", schema = "production")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class YarnArticleAudit extends BaseEntity {

  public static final short PAYLOAD_SCHEMA_VERSION = 1;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "article_id", nullable = false, updatable = false)
  private YarnArticle article;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 30, updatable = false)
  private YarnArticleAuditEventType eventType;

  @Column(name = "spec_version_from", nullable = false, updatable = false)
  private int specVersionFrom;

  @Column(name = "spec_version_to", nullable = false, updatable = false)
  private int specVersionTo;

  @Column(name = "payload_schema_version", nullable = false, updatable = false)
  private short payloadSchemaVersion;

  @Type(JsonType.class)
  @Column(name = "spec_after", nullable = false, columnDefinition = "jsonb", updatable = false)
  private JsonNode specAfter;

  @Type(JsonType.class)
  @Column(name = "changed_summary", nullable = false, columnDefinition = "jsonb", updatable = false)
  private JsonNode changedSummary;

  public static YarnArticleAudit create(
      YarnArticle article,
      YarnArticleAuditEventType eventType,
      int from,
      int to,
      JsonNode specAfter,
      JsonNode changedSummary) {
    YarnArticleAudit audit = new YarnArticleAudit();
    audit.article = article;
    audit.eventType = eventType;
    audit.specVersionFrom = from;
    audit.specVersionTo = to;
    audit.payloadSchemaVersion = PAYLOAD_SCHEMA_VERSION;
    audit.specAfter = specAfter.deepCopy();
    audit.changedSummary = changedSummary.deepCopy();
    return audit;
  }

  public UUID getArticleId() {
    return article == null ? null : article.getId();
  }

  @Override
  protected String getModuleCode() {
    return "YAUD";
  }
}
