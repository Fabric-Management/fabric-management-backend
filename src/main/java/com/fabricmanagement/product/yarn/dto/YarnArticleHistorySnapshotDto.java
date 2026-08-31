package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.domain.article.YarnArticleAudit;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleAuditEventType;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Reconstructible yarn article specification at one spec version")
public record YarnArticleHistorySnapshotDto(
    int specVersion,
    YarnArticleAuditEventType eventType,
    UUID actorId,
    Instant timestamp,
    JsonNode specAfter,
    JsonNode changedSummary) {

  public static YarnArticleHistorySnapshotDto from(YarnArticleAudit audit) {
    return new YarnArticleHistorySnapshotDto(
        audit.getSpecVersionTo(),
        audit.getEventType(),
        audit.getCreatedBy(),
        audit.getCreatedAt(),
        audit.getSpecAfter(),
        audit.getChangedSummary());
  }
}
