package com.fabricmanagement.sales.salesorder.domain.port;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

/** Consumer-owned typed boundary for reconstructing a pinned yarn article specification. */
public interface YarnArticleSpecHistoryPort {

  Snapshot historyVersion(UUID articleId, int specificationVersion);

  record Snapshot(
      UUID articleId,
      int specificationVersion,
      JsonNode specification,
      UUID actorId,
      Instant recordedAt) {
    public Snapshot {
      if (articleId == null
          || specificationVersion < 1
          || specification == null
          || specification.isNull()
          || recordedAt == null) {
        throw new IllegalArgumentException("Complete yarn specification history snapshot required");
      }
    }
  }
}
