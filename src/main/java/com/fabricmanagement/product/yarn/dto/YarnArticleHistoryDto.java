package com.fabricmanagement.product.yarn.dto;

import com.fabricmanagement.product.yarn.domain.article.YarnArticleAudit;
import com.fabricmanagement.product.yarn.domain.article.YarnArticleAuditEventType;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "One immutable yarn article audit event")
public record YarnArticleHistoryDto(
    UUID id,
    int specVersionFrom,
    int specVersionTo,
    YarnArticleAuditEventType eventType,
    UUID actorId,
    Instant timestamp,
    JsonNode changedSummary) {

  public static YarnArticleHistoryDto from(YarnArticleAudit audit) {
    return new YarnArticleHistoryDto(
        audit.getId(),
        audit.getSpecVersionFrom(),
        audit.getSpecVersionTo(),
        audit.getEventType(),
        audit.getCreatedBy(),
        audit.getCreatedAt(),
        audit.getChangedSummary());
  }
}
